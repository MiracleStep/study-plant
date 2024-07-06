package com.tianji.learning.service;

import com.tianji.learning.domain.vo.SignResultVO;

public interface ISignRecordService {
    /**
     * 签到
     * @return
     */
    SignResultVO addSignRecords();
}
