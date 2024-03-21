package ui.services;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Tooltip;
import taiga.model.query.sprint.Sprint;
import taiga.model.query.sprint.UserStoryDetail;
import taiga.model.query.userstories.UserStoryInterface;
import taiga.util.timeAnalysis.CycleTimeEntry;
import taiga.util.timeAnalysis.LeadTimeHelper;
import taiga.util.timeAnalysis.LeadTimeStats;
import taiga.util.timeAnalysis.LeadTimeStoryEntry;
import ui.util.DateUtil;

public class LeadTimeService extends Service<Object> {
    private Sprint sprint;

    private final ObservableList<XYChart.Data<String, Number>> storyLeadTimes;
    private final ObservableList<XYChart.Data<String, Number>> notCreatedStories;
    private final ObservableList<XYChart.Data<String, Number>> inBacklogStories;
    private final ObservableList<XYChart.Data<String, Number>> inSprintStories;
    private final ObservableList<XYChart.Data<String, Number>> inProgressStories;
    private final ObservableList<XYChart.Data<String, Number>> readyForTestStories;
    private final ObservableList<XYChart.Data<String, Number>> doneStories;

    public LeadTimeService() {
        this.storyLeadTimes = FXCollections.observableArrayList();
        this.notCreatedStories = FXCollections.observableArrayList();
        this.inBacklogStories = FXCollections.observableArrayList();
        this.inSprintStories = FXCollections.observableArrayList();
        this.inProgressStories = FXCollections.observableArrayList();
        this.readyForTestStories = FXCollections.observableArrayList();
        this.doneStories = FXCollections.observableArrayList();
    }

    public ObservableList<XYChart.Data<String, Number>> getStoryLeadTimes() {
        return storyLeadTimes;
    }

    public ObservableList<XYChart.Data<String, Number>> getNotCreatedStories() {
        return notCreatedStories;
    }

    public ObservableList<XYChart.Data<String, Number>> getInBacklogStories() {
        return inBacklogStories;
    }

    public ObservableList<XYChart.Data<String, Number>> getInProgressStories() {
        return inSprintStories;
    }

    public ObservableList<XYChart.Data<String, Number>> getInSprintStories() {
        return inProgressStories;
    }

    public ObservableList<XYChart.Data<String, Number>> getReadyForTestStories() {
        return readyForTestStories;
    }

    public ObservableList<XYChart.Data<String, Number>> getDoneStories() {
        return doneStories;
    }

    public void recalculate(Sprint sprint) {
        this.sprint = sprint;
        this.restart();
    }

    private List<LeadTimeStats> getAllLeadTimeStats() {
        LeadTimeHelper leadTimeHelper = new LeadTimeHelper(this.sprint.getProject());

        LocalDate start = DateUtil.toLocal(sprint.getEstimatedStart());
        LocalDate end = DateUtil.toLocal(sprint.getEstimatedFinish());
        List<LocalDate> dates = start.datesUntil(end.plusDays(1)).toList();

        List<LeadTimeStats> leadTimeStats = new ArrayList<>();

        for (LocalDate date : dates) {
            leadTimeStats.add(leadTimeHelper.getLeadTimeStatsForDate(DateUtil.toDate(date)));
        }
        return leadTimeStats;
    }

    @Override
    protected void failed() {
        super.failed();
        Throwable exception = getException();
        if (exception != null) {
            exception.printStackTrace();
        }
    }

    private List<LeadTimeStoryEntry> getAllStoryLeadTimes() {
        LeadTimeHelper leadTimeHelper = new LeadTimeHelper(this.sprint.getProject());
        LocalDate start = DateUtil.toLocal(sprint.getEstimatedStart());
        LocalDate end = DateUtil.toLocal(sprint.getEstimatedFinish());
        List<LocalDate> dates = start.datesUntil(end.plusDays(1)).toList();
        List<LeadTimeStoryEntry> applicableStories = leadTimeHelper.getAllStoryLeadTimes()
                .stream()
                .filter(story -> story.getEndDate() != null && story.getEndDate().before(DateUtil.toDate(end.plusDays(1))) && story.getEndDate().after(sprint.getEstimatedStart()))
                .toList();
        List<LeadTimeStoryEntry> finalStoryLeadTimes = new ArrayList<>(applicableStories);
        for (LocalDate date : dates) {
            finalStoryLeadTimes.add(new LeadTimeStoryEntry(null, DateUtil.toDate(date), DateUtil.toDate(date), false));
        }
        return finalStoryLeadTimes;
    }

    private void updateStoryLeadTimes(ObservableList<XYChart.Data<String, Number>> data, List<LeadTimeStoryEntry> entries) {
        SimpleDateFormat format = new SimpleDateFormat("MMM dd");
        List<LeadTimeStoryEntry> sortedEntries = entries.stream()
                .sorted(Comparator.comparing(LeadTimeStoryEntry::getEndDate))
                .toList();
        data.setAll(
                sortedEntries
                        .stream()
                        .map(story -> {
                            if (story.isValid()) {
                                return new XYChart.Data<>(format.format(story.getEndDate()), (Number) story.getDaysTaken());
                            }
                            return new XYChart.Data<>(format.format(story.getEndDate()), (Number) 0);
                        })
                        .toList()
        );
        for (int i = 0; i < data.size(); i++) {
            XYChart.Data<String, Number> d = data.get(i);
            LeadTimeStoryEntry story = sortedEntries.get(i);
            UserStoryDetail storyDetail = (UserStoryDetail) story.get();
            if (!story.isValid()) {
                d.getNode().setVisible(false);
            } else {
                Tooltip.install(d.getNode(), new Tooltip(
                        storyDetail.getSubject() + " (#" + storyDetail.getRef() + ")"
                                + "\nStarted on: " + story.getStartDate()
                                + "\nCompleted on: " + story.getEndDate()
                                + "\nLead Time: " + story.getDaysTaken()));
            }
        }
    }

    private void updateLeadTimes(ObservableList<XYChart.Data<String, Number>> data, List<LeadTimeStats> entries, LeadTimeCallback callback) {
        SimpleDateFormat format = new SimpleDateFormat("MMM dd");

        data.setAll(
                entries.stream()
                        .sorted(Comparator.comparing(LeadTimeStats::getDate))
                        .map(e -> {
                            return new XYChart.Data<>(format.format(e.getDate()), (Number) callback.getStories(e).size());
                        })
                        .toList()
        );
    }


    @Override
    protected Task<Object> createTask() {
        return new Task<Object>() {
            @Override
            protected Object call() throws Exception {
                if (sprint == null) {
                    return null;
                }

                List<LeadTimeStats> stats = getAllLeadTimeStats();
                List<LeadTimeStoryEntry> allStoryLeadTimes = getAllStoryLeadTimes();

                Platform.runLater(() -> {
                    updateStoryLeadTimes(storyLeadTimes, allStoryLeadTimes);
                    updateLeadTimes(notCreatedStories, stats, LeadTimeStats::getNotCreated);
                    updateLeadTimes(inBacklogStories, stats, LeadTimeStats::getInBacklog);
                    updateLeadTimes(inSprintStories, stats, LeadTimeStats::getInSprint);
                    updateLeadTimes(inProgressStories, stats, LeadTimeStats::getInProgress);
                    updateLeadTimes(readyForTestStories, stats, LeadTimeStats::getInTest);
                    updateLeadTimes(doneStories, stats, LeadTimeStats::getInDone);

                });

                return null;
            }
        };
    }

    interface LeadTimeCallback {
        List<UserStoryInterface> getStories(LeadTimeStats stats);
    }
}
