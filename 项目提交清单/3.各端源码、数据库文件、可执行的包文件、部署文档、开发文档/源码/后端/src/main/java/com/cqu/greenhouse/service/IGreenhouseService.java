package com.cqu.greenhouse.service;

import com.cqu.greenhouse.entity.*;
import com.cqu.greenhouse.sim.LightFieldModel;

import java.util.List;
import java.util.Map;

public interface IGreenhouseService {
    List<GhZone> listZones();

    Map<String, Object> getZoneEffectiveLight(String zoneId);

    List<GhRecipe> listRecipes();

    GhRecipe getRecipe(String recipeId);

    void bindRecipe(String zoneId, String recipeId);

    void setClimateProfile(String zoneId, String profileId);

    void setAutoControl(String zoneId, boolean enabled);

    List<GhDevice> listDevices(String zoneId);

    List<GhWorkOrder> listWorkOrders(String status);

    void approveWorkOrder(Long id);

    void rejectWorkOrder(Long id);

    /** 接单并下发执行器（APPROVED → COMPLETED） */
    void claimWorkOrder(Long id);

    void completeWorkOrder(Long id);

    void setDimming(String deviceSn, int percent, String source);

    void setShadeOpen(String deviceSn, int percent, String source);

    void ingestTelemetry(Map<String, Object> payload);

    void ingestStatus(Map<String, Object> payload);

    void ingestAlarm(Map<String, Object> payload);

    /** 仿真一步：推进光场 + 规则 */
    void tickSimulation();

    void resetSimDay();

    Map<String, Object> getSimClock();

    LightFieldModel.FieldResult previewField(String zoneId);

    List<GhControlLog> recentControlLogs(int limit, String source);

    List<GhAlarm> listAlarms(String status, int limit);

    void resolveAlarm(Long id);

    List<GhReport> listReports(String type, String status, int limit);

    GhReport getReport(Long id);

    /** 按当日光场/工单生成或刷新 DAILY_LIGHT 草稿（学员请用实训草稿） */
    GhReport draftDailyLight(String zoneId);

    /** 学员实训报告草稿 TRAINING */
    GhReport draftTraining(String zoneId);

    void submitReport(Long id);

    void reviewReport(Long id, String note, boolean approve);

    Map<String, Object> climateProfiles();
}
