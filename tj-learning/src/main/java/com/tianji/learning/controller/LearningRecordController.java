package com.tianji.learning.controller;


import com.tianji.api.dto.leanring.LearningLessonDTO;
import com.tianji.learning.domain.dto.LearningRecordFormDTO;
import com.tianji.learning.service.ILearningLessonService;
import com.tianji.learning.service.ILearningRecordService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 学习记录表 前端控制器
 * </p>
 *
 * @author mirac
 * @since 2024-06-27
 */
@Api(tags = "学习记录相关接口")
@RestController
@RequestMapping("/learning-records")
@RequiredArgsConstructor
public class LearningRecordController {

    private final ILearningRecordService recordService;

    /**
     * 查询当前用户指定课程的学习进度
     * 查询学习记录
     * @param courseId 课程id
     * @return 课表信息、学习记录及进度信息
     */
    @ApiOperation("查询当前用户指定课程的学习进度")
    @GetMapping("/course/{courseId}")
    LearningLessonDTO queryLearningRecordByCourse(@PathVariable("courseId") Long courseId) {
        return recordService.queryLearningRecordByCourse(courseId);
    }

    /**
     * 提交学习记录
     */
    @ApiOperation("提交学习记录")
    @PostMapping()
    public void addLearningRecord(@RequestBody @Validated LearningRecordFormDTO dto) {
        recordService.addLearningRecord(dto);
    }
}
