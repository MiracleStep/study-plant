package com.tianji.learning.controller;

import com.tianji.learning.domain.vo.SignResultVO;
import com.tianji.learning.service.ISignRecordService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 签到控制器
 */
@Api(tags = "签到相关接口")
@RestController
@RequestMapping("/sign-records")
@AllArgsConstructor
public class SignRecordsController {

    private final ISignRecordService signRecordService;

    @ApiOperation("签到")
    @PostMapping
    public SignResultVO addSignRecords() {
        return signRecordService.addSignRecords();
    }
}
