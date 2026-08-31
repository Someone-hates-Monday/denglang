package com.cqu.greenhouse.controller;

import com.cqu.greenhouse.entity.GhAlarm;
import com.cqu.greenhouse.entity.GhControlLog;
import com.cqu.greenhouse.entity.GhDevice;
import com.cqu.greenhouse.entity.GhRecipe;
import com.cqu.greenhouse.entity.GhReport;
import com.cqu.greenhouse.entity.GhWorkOrder;
import com.cqu.greenhouse.entity.GhZone;
import com.cqu.greenhouse.service.IGreenhouseService;
import com.cqu.security.RoleCapabilities;
import com.cqu.security.RequireCap;
import com.cqu.utils.UserHolder;
import com.cqu.vo.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/greenhouse")
public class GreenhouseController {

    @Autowired
    private IGreenhouseService greenhouseService;

    @GetMapping("/zones")
    public Result<List<GhZone>> zones() {
        return Result.success(greenhouseService.listZones());
    }

    @GetMapping("/zones/{zoneId}/effective-light")
    public Result<Map<String, Object>> effectiveLight(@PathVariable String zoneId) {
        try {
            return Result.success(greenhouseService.getZoneEffectiveLight(zoneId));
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        }
    }

    @RequireCap({RoleCapabilities.RECIPE_BIND})
    @PutMapping("/zones/{zoneId}/recipe")
    public Result<String> bindRecipe(@PathVariable String zoneId, @RequestBody Map<String, String> body) {
        try {
            greenhouseService.bindRecipe(zoneId, body.get("recipeId"));
            return Result.success("ok");
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        }
    }

    @RequireCap({RoleCapabilities.CLIMATE_SET})
    @PutMapping("/zones/{zoneId}/climate-profile")
    public Result<String> climate(@PathVariable String zoneId, @RequestBody Map<String, String> body) {
        try {
            greenhouseService.setClimateProfile(zoneId, body.get("profileId"));
            return Result.success("ok");
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        }
    }

    @RequireCap({RoleCapabilities.AUTO_TOGGLE})
    @PutMapping("/zones/{zoneId}/auto-control")
    public Result<String> autoControl(@PathVariable String zoneId, @RequestBody Map<String, Object> body) {
        try {
            boolean enabled = Boolean.parseBoolean(String.valueOf(body.get("enabled")));
            greenhouseService.setAutoControl(zoneId, enabled);
            return Result.success("ok");
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        }
    }

    @GetMapping("/recipes")
    public Result<List<GhRecipe>> recipes() {
        return Result.success(greenhouseService.listRecipes());
    }

    @GetMapping("/recipes/{recipeId}")
    public Result<GhRecipe> recipe(@PathVariable String recipeId) {
        GhRecipe r = greenhouseService.getRecipe(recipeId);
        if (r == null) {
            return Result.fail("配方不存在");
        }
        return Result.success(r);
    }

    @GetMapping("/devices")
    public Result<List<GhDevice>> devices(@RequestParam(required = false) String zoneId) {
        return Result.success(greenhouseService.listDevices(zoneId));
    }

    @PostMapping("/lamps/{sn}/dimming")
    public Result<String> dimming(@PathVariable String sn, @RequestBody Map<String, Object> body) {
        try {
            int pct = Integer.parseInt(String.valueOf(body.get("dimmingPercent")));
            RoleCapabilities.requireDimming(UserHolder.getRole(), pct);
            greenhouseService.setDimming(sn, pct, "MANUAL");
            return Result.success("ok");
        } catch (Exception e) {
            return Result.fail(e instanceof com.cqu.security.ForbiddenException ? 403 : 500, e.getMessage());
        }
    }

    @RequireCap({RoleCapabilities.CTRL_SHADE})
    @PostMapping("/shades/{sn}/open-percent")
    public Result<String> shade(@PathVariable String sn, @RequestBody Map<String, Object> body) {
        try {
            int pct = Integer.parseInt(String.valueOf(body.get("shadeOpenPercent")));
            greenhouseService.setShadeOpen(sn, pct, "MANUAL");
            return Result.success("ok");
        } catch (Exception e) {
            return Result.fail(e.getMessage());
        }
    }

    @GetMapping("/work-orders")
    public Result<List<GhWorkOrder>> workOrders(@RequestParam(required = false) String status) {
        return Result.success(greenhouseService.listWorkOrders(status));
    }

    @RequireCap({RoleCapabilities.WO_APPROVE})
    @PostMapping("/work-orders/{id}/approve")
    public Result<String> approve(@PathVariable Long id) {
        try {
            greenhouseService.approveWorkOrder(id);
            return Result.success("ok");
        } catch (Exception e) {
            return Result.fail(e.getMessage());
        }
    }

    @RequireCap({RoleCapabilities.WO_REJECT})
    @PostMapping("/work-orders/{id}/reject")
    public Result<String> reject(@PathVariable Long id) {
        try {
            greenhouseService.rejectWorkOrder(id);
            return Result.success("ok");
        } catch (Exception e) {
            return Result.fail(e.getMessage());
        }
    }

    /** 种植员接单执行：下发执行器并完成工单（R1 合并 claim+execute） */
    @RequireCap({RoleCapabilities.WO_CLAIM})
    @PostMapping("/work-orders/{id}/claim")
    public Result<String> claim(@PathVariable Long id) {
        try {
            greenhouseService.claimWorkOrder(id);
            return Result.success("ok");
        } catch (Exception e) {
            return Result.fail(e.getMessage());
        }
    }

    @RequireCap({RoleCapabilities.WO_COMPLETE})
    @PostMapping("/work-orders/{id}/complete")
    public Result<String> complete(@PathVariable Long id) {
        try {
            greenhouseService.completeWorkOrder(id);
            return Result.success("ok");
        } catch (Exception e) {
            return Result.fail(e.getMessage());
        }
    }

    @RequireCap({RoleCapabilities.LOG_VIEW})
    @GetMapping("/control-logs")
    public Result<List<GhControlLog>> logs(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String source) {
        return Result.success(greenhouseService.recentControlLogs(limit, source));
    }

    @RequireCap({RoleCapabilities.LOG_VIEW})
    @GetMapping("/alarms")
    public Result<List<GhAlarm>> alarms(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "50") int limit) {
        return Result.success(greenhouseService.listAlarms(status, limit));
    }

    @RequireCap({RoleCapabilities.LOG_VIEW})
    @PutMapping("/alarms/{id}/resolve")
    public Result<String> resolveAlarm(@PathVariable Long id) {
        try {
            greenhouseService.resolveAlarm(id);
            return Result.success("ok");
        } catch (Exception e) {
            return Result.fail(e.getMessage());
        }
    }

    @RequireCap({RoleCapabilities.REPORT_VIEW})
    @GetMapping("/reports")
    public Result<List<GhReport>> reports(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "50") int limit) {
        return Result.success(greenhouseService.listReports(type, status, limit));
    }

    @RequireCap({RoleCapabilities.REPORT_VIEW})
    @GetMapping("/reports/{id}")
    public Result<GhReport> report(@PathVariable Long id) {
        GhReport r = greenhouseService.getReport(id);
        if (r == null) {
            return Result.fail("报告不存在");
        }
        return Result.success(r);
    }

    @RequireCap({RoleCapabilities.REPORT_WRITE})
    @PostMapping("/reports/daily-draft")
    public Result<GhReport> dailyDraft(@RequestBody(required = false) Map<String, String> body) {
        try {
            String zoneId = body != null ? body.get("zoneId") : null;
            return Result.success(greenhouseService.draftDailyLight(zoneId));
        } catch (Exception e) {
            return Result.fail(e.getMessage());
        }
    }

    @RequireCap({RoleCapabilities.REPORT_WRITE})
    @PostMapping("/reports/training-draft")
    public Result<GhReport> trainingDraft(@RequestBody(required = false) Map<String, String> body) {
        try {
            String zoneId = body != null ? body.get("zoneId") : null;
            return Result.success(greenhouseService.draftTraining(zoneId));
        } catch (Exception e) {
            return Result.fail(e.getMessage());
        }
    }

    @RequireCap({RoleCapabilities.REPORT_WRITE})
    @PostMapping("/reports/{id}/submit")
    public Result<String> submitReport(@PathVariable Long id) {
        try {
            greenhouseService.submitReport(id);
            return Result.success("ok");
        } catch (Exception e) {
            return Result.fail(e.getMessage());
        }
    }

    @RequireCap({RoleCapabilities.PERM_DECIDE})
    @PostMapping("/reports/{id}/review")
    public Result<String> reviewReport(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        try {
            String note = body.get("note") != null ? String.valueOf(body.get("note")) : "";
            boolean approve = body.get("approve") == null || Boolean.parseBoolean(String.valueOf(body.get("approve")));
            greenhouseService.reviewReport(id, note, approve);
            return Result.success("ok");
        } catch (Exception e) {
            return Result.fail(e.getMessage());
        }
    }

    @GetMapping("/climate-profiles")
    public Result<Map<String, Object>> profiles() {
        return Result.success(greenhouseService.climateProfiles());
    }

    @PostMapping("/sim/tick")
    public Result<String> tick() {
        greenhouseService.tickSimulation();
        return Result.success("ok");
    }

    @RequireCap({RoleCapabilities.SIM_RESET})
    @PostMapping("/sim/reset-day")
    public Result<String> resetDay() {
        greenhouseService.resetSimDay();
        return Result.success("ok");
    }

    @GetMapping("/sim/clock")
    public Result<Map<String, Object>> clock() {
        return Result.success(greenhouseService.getSimClock());
    }
}
