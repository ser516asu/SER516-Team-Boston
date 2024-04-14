package ui.metrics.burndown;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableMap;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.StackPane;
import org.kordamp.ikonli.boxicons.BoxiconsRegular;
import taiga.models.sprint.Sprint;
import ui.components.Icon;
import ui.services.BurndownService;

import java.util.ArrayList;
import java.util.List;

public class Burndown extends StackPane {
    private final BurndownService service;
    private final TabPane tabPane;

    public Burndown() {
        this.service = new BurndownService();
        this.tabPane = new TabPane();
        this.init();
    }

    private void init() {
        Tab taskBurndownTab = createBurndownTab("Task", "Fractional Story Points", new Icon(BoxiconsRegular.CLIPBOARD), this.service.getTaskData());
        Tab usBurndownTab = createBurndownTab("User Story", "Full Story Points", new Icon(BoxiconsRegular.USER), this.service.getUserStoryData());
        Tab bvBurndownTab = createBurndownTab("Business Value", "Business Value Points", new Icon(BoxiconsRegular.BRIEFCASE), this.service.getBusinessValueData());
        Tab combinedBurndownTab = createCombinedBurndownTab(this.service.getTaskData(), this.service.getUserStoryData(), this.service.getBusinessValueData());

        tabPane.getTabs().setAll(taskBurndownTab, usBurndownTab, bvBurndownTab, combinedBurndownTab);
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        getChildren().add(tabPane);
        this.service.start();
    }

    private Tab createBurndownTab(String name, String valueUnits, Icon icon, ObservableMap<Sprint, BurndownService.Data> dataMap) {
        StackPane root = new StackPane();

        Tab tab = new Tab(name);
        tab.setGraphic(icon);

        ProgressIndicator progress = new ProgressIndicator(-1d);
        progress.visibleProperty().bind(this.service.runningProperty());

        CategoryAxis date = new CategoryAxis();
        date.setLabel("Date");
        NumberAxis value = new NumberAxis();
        value.setLabel(valueUnits);

        AreaChart<String, Number> chart = new AreaChart<>(date, value);

        dataMap.addListener((MapChangeListener.Change<? extends Sprint, ? extends BurndownService.Data> change) -> {
            if (change.wasRemoved()) {
                chart.getData().removeIf(series -> {
                    return series.getName().equals("Ideal (" + change.getKey().getName() + ")") || series.getName().equals("Current (" + change.getKey().getName() + ")");
                });
            }
            if (change.wasAdded()) {
                Sprint sprint = change.getKey();
                BurndownService.Data data = change.getValueAdded();

                XYChart.Series<String, Number> ideal = new XYChart.Series<>(data.getIdeal());
                ideal.setName("Ideal (" + sprint.getName() + ")");
                XYChart.Series<String, Number> current = new XYChart.Series<>(data.getCalculated());
                current.setName("Current (" + sprint.getName() + ")");

                chart.getData().add(ideal);
                chart.getData().add(current);
            }
        });


        chart.setAnimated(false);
        chart.visibleProperty().bind(this.service.runningProperty().not());

        root.getChildren().add(chart);
        root.getChildren().add(progress);

        tab.setContent(root);

        return tab;
    }
  

    /**
     * Choose which sprints will be displayed in the burndown graphs
     *
     * @param sprints the list of sprints to display the various burndown graphs for
     */
    public void selectSprints(List<Sprint> sprints) {
        this.selectSprints(sprints, false);
    }

    /**
     * Choose which sprints will be displayed in the burndown graphs
     *
     * @param sprints the list of sprints to display the various burndown graphs for
     * @param overlay Whether or not to make the burndown charts overlay one another. if false the
     *                burndown charts will be displayed chronologically.
     */
    public void selectSprints(List<Sprint> sprints, boolean overlay) {
        this.service.recalculate(sprints, overlay);
    }


    private Tab createCombinedBurndownTab(ObservableMap<Sprint, BurndownService.Data> taskDataMap, ObservableMap<Sprint, BurndownService.Data> userDataMap, ObservableMap<Sprint, BurndownService.Data> bizDataMap) {
        Tab tab = new Tab("Combined");
        tab.setGraphic(new Icon(BoxiconsRegular.CHART));
    
        CategoryAxis dateAxis = new CategoryAxis();
        dateAxis.setLabel("Date");
        NumberAxis pointsAxis = new NumberAxis();
        pointsAxis.setLabel("Points");
    
        AreaChart<String, Number> chart = new AreaChart<>(dateAxis, pointsAxis);
        chart.setTitle("Combined Burndown Chart");
    
        // create a series for each type of burndown data
        createAndAddSeries(chart, taskDataMap, "Task Burndown");
        createAndAddSeries(chart, userDataMap, "User Story Burndown");
        createAndAddSeries(chart, bizDataMap, "Business Value Burndown");
    
        StackPane root = new StackPane(chart);
        tab.setContent(root);
    
        return tab;
    }
    
    private void createAndAddSeries(AreaChart<String, Number> chart, ObservableMap<Sprint, BurndownService.Data> dataMap, String seriesName) {
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName(seriesName);
    
        dataMap.addListener((MapChangeListener.Change<? extends Sprint, ? extends BurndownService.Data> change) -> {
            if (change.wasAdded()) {
                BurndownService.Data data = change.getValueAdded();
    
                List<XYChart.Data<String, Number>> seriesData = new ArrayList<>();
                for (XYChart.Data<String, Number> datum : data.getCalculated()) {
                    seriesData.add(new XYChart.Data<>(datum.getXValue(), datum.getYValue()));
                }
    
                if (chart.getData().contains(series)) {
                    series.getData().setAll(seriesData);
                } else {
                    series.getData().addAll(seriesData);
                    chart.getData().add(series);
                }
            }
        });
    }
    



    public void focusFirstTab() {
        tabPane.getSelectionModel().selectFirst();
    }

    public ReadOnlyBooleanProperty serviceRunning() {
        return this.service.runningProperty();
    }

    public void cancel() {
        this.service.cancel();
    }
}
