package com.cqu.mapper;

import com.cqu.entity.LightReadings;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 光照强度采集记录（时序数据） Mapper 接口
 * </p>
 *
 * @author 
 * @since 2026-06-29
 */
@Mapper
public interface LightReadingsMapper extends BaseMapper<LightReadings> {

}
