package com.cqu.greenhouse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("gh_reports")
public class GhReport implements Serializable {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String reportType;
    private String title;
    private String status;
    private Long authorId;
    private String authorRole;
    private String zoneId;
    private LocalDate reportDate;
    private String summaryZh;
    private String bodyJson;
    private String workOrderIds;
    private Long reviewerId;
    private String reviewNote;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
