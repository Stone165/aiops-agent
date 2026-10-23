package com.aiops.agent.mapper;

import org.apache.ibatis.annotations.Mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import com.aiops.agent.entity.Incident;

@Mapper 
public interface IncidentMapper extends BaseMapper<Incident>{
}
