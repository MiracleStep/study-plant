package com.tianji.remark.controller;


import com.tianji.remark.domain.dto.LikeRecordFormDTO;
import com.tianji.remark.service.ILikedRecordService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.AllArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * <p>
 * 点赞记录表 前端控制器
 * </p>
 *
 * @author mirac
 * @since 2024-07-04
 */
@Api(tags = "点赞相关接口")
@RestController
@RequestMapping("/likes")
@AllArgsConstructor
public class LikedRecordController {

    private final ILikedRecordService likedRecordService;
    @ApiOperation("点赞或取消赞")
    @PostMapping
    public void addLikeRecord(@RequestBody @Validated LikeRecordFormDTO dto) {
        likedRecordService.addLikeRecord(dto);
    }

    @ApiOperation("批量查询用户点赞状态")//feign远程调用接口
    @GetMapping("list")
    public Set<Long> getLikesStatusByBizIds(@RequestParam("bizIds") List<Long> bizIds) {
        return likedRecordService.getLikesStatusByBizIds(bizIds);
    }
}
