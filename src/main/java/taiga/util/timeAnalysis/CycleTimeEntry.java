package taiga.util.timeAnalysis;

import taiga.model.query.userstories.UserStoryInterface;

import java.util.Date;
import java.util.concurrent.TimeUnit;

public class CycleTimeEntry<T> {
    private final T story;
    private final Date startDate;
    private final Date endDate;
    private final boolean valid;

    public CycleTimeEntry(T story, Date start, Date end, boolean valid){
        this.story = story;
        this.startDate = start;
        this.endDate = end;
        this.valid = valid;
    }

    public CycleTimeEntry(T story, Date start, Date end) {
        this(story, start, end, true);
    }

    public Date getStartDate() {
        return startDate;
    }

    public Date getEndDate() {
        return endDate;
    }

    public Long getTimeTaken(){
        if(startDate == null || endDate == null){
            return 0L;
        }
        return endDate.getTime() - startDate.getTime();
    }

    public T get() {
        return story;
    }

    public Long getDaysTaken() {
        return TimeUnit.MILLISECONDS.toDays(getTimeTaken());
    }

    public boolean isValid() {
        return valid;
    }

    @Override
    public String toString() {
        return "CycleTimeEntry{" +
                "startDate=" + startDate +
                ", endDate=" + endDate +
                '}';
    }
}
