package com.cqu.service;

import com.cqu.entity.ControlLogs;
import com.baomidou.mybatisplus.extension.service.IService;
import com.cqu.vo.ControlLogVO;
import com.cqu.vo.PageResult;

/**
 * <p>
 * 路灯控制指令日志 服务类
 * </p>
 *
 * @author
 * @since 2026-06-29
 */
public interface IControlLogsService extends IService<ControlLogs> {

    /**
     * 控制日志分页列表（按设备/操作类型/操作人/来源筛选，按时间倒序）
     */
    PageResult<ControlLogVO> pageLogs(int page, int pageSize, Long deviceId, String command,
                                      Long operatorId, String source);

    /**
     * 控制日志详情
     */
    ControlLogVO getDetail(Long id);

    /**
     * 记录操作日志（立即成功，无硬件回执等待 — 如增删设备）
     */
    void recordLog(Long deviceId, String command, String result);

    /**
     * 记录操作日志（指定来源，立即成功）
     */
    void recordLog(Long deviceId, String command, String result, String source);

    /**
     * 记录待确认的下发指令（PENDING），等待板端 status 回传匹配 expectedStatus
     *
     * @return 控制日志 ID
     */
    Long recordPendingCommand(Long deviceId, String command, String source, String expectedStatus);

    /**
     * 新指令下发前，取消同设备上仍未完成的 PENDING，避免误报 COMMAND_TIMEOUT
     */
    void supersedePendingCommands(Long deviceId);

    /**
     * 板端 status 回传时，将最近一条匹配的 PENDING 指令标记为 SUCCESS
     */
    void confirmPendingByStatus(Long deviceId, String status);
}
