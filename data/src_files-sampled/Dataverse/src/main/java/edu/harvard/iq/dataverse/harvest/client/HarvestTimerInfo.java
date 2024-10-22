package edu.harvard.iq.dataverse.harvest.client;

import java.io.Serializable;

public class HarvestTimerInfo implements Serializable {
    private Long harvestingClientId;
    private String name;
    private String schedulePeriod;
    private Integer scheduleHourOfDay;
    
    public HarvestTimerInfo() {
        
    }
    
   
    public HarvestTimerInfo(Long harvestingClientId, String name, String schedulePeriod, Integer scheduleHourOfDay, Integer scheduleDayOfWeek) {
        this.harvestingClientId=harvestingClientId;
        this.name=name;
        this.schedulePeriod=schedulePeriod;
        this.scheduleDayOfWeek=scheduleDayOfWeek;
        this.scheduleHourOfDay=scheduleHourOfDay;
    }
    
    
    public Long getHarvestingClientId() {
        return harvestingClientId;
    }

    public void setHarvestingClientId(Long harvestingClientId) {
        this.harvestingClientId = harvestingClientId;
    }    
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSchedulePeriod() {
        return schedulePeriod;
    }

    public void setSchedulePeriod(String schedulePeriod) {
        this.schedulePeriod = schedulePeriod;
    }

    public Integer getScheduleHourOfDay() {
        return scheduleHourOfDay;
    }

    public void setScheduleHourOfDay(Integer scheduleHourOfDay) {
        this.scheduleHourOfDay = scheduleHourOfDay;
    }

    public Integer getScheduleDayOfWeek() {
        return scheduleDayOfWeek;
    }

    public void setScheduleDayOfWeek(Integer scheduleDayOfWeek) {
        this.scheduleDayOfWeek = scheduleDayOfWeek;
    }
    private Integer scheduleDayOfWeek;
  
    
}
