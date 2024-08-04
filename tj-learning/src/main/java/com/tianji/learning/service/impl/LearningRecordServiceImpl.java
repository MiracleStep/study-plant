package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.leanring.LearningLessonDTO;
import com.tianji.api.dto.leanring.LearningRecordDTO;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.LearningRecordFormDTO;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.enums.SectionType;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.service.ILearningLessonService;
import com.tianji.learning.service.ILearningRecordService;
import com.tianji.learning.utils.LearningRecordDelayTaskHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 * 学习记录表 服务实现类
 * </p>
 *
 * @author mirac
 * @since 2024-06-27
 */
@Service
@RequiredArgsConstructor
public class LearningRecordServiceImpl extends ServiceImpl<LearningRecordMapper, LearningRecord> implements ILearningRecordService {

    private final ILearningLessonService lessonService;

    private final CourseClient courseClient;

    private final LearningRecordDelayTaskHandler taskHandler;

    @Override
    public LearningLessonDTO queryLearningRecordByCourse(Long courseId) {
        //1.获取当前登录用户id
        Long userId = UserContext.getUser();
        //2.查询课表信息 条件user_id 和 courseId
        LearningLesson lesson = lessonService.lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, courseId)
                .one();
        if (lesson == null) {
            throw new BizIllegalException("该课程未加入课表");
        }
        //3.查询学习记录 条件lesson_id 和 userId
        List<LearningRecord> recordList = this.lambdaQuery()
                .eq(LearningRecord::getLessonId, lesson.getId())
                .eq(LearningRecord::getUserId, userId)
                .list();
        //4.封装结果返回
        LearningLessonDTO dto = new LearningLessonDTO();
        dto.setId(lesson.getId());
        dto.setLatestSectionId(lesson.getLatestSectionId());
        List<LearningRecordDTO> dtoList = BeanUtils.copyList(recordList, LearningRecordDTO.class);
        dto.setRecords(dtoList);
        return dto;
    }
    @Override
    public void addLearningRecord(LearningRecordFormDTO dto) {
        //1.获取当前登录用户的id
        Long userId = UserContext.getUser();
        //2.处理学习记录
        boolean isFinished = false;//代表本小姐是否已经学完
        if (dto.getSectionType().equals(SectionType.VIDEO)) {
            //2.1提交视频播放记录
            isFinished = handleVideoRecord(userId, dto);
        } else {
            //2.2 提交考试记录
            isFinished = handleExamRecord(userId, dto);
        }
        //3.处理课表数据
        handleLessonData(dto, isFinished);
    }

    @Override
    public void addLearningRecordV2(LearningRecordFormDTO dto) {
        //1.获取当前登录用户的id
        Long userId = UserContext.getUser();
        //2.处理学习记录
        boolean isFirstFinished = false;//代表本小姐是否已经学完
        if (dto.getSectionType().equals(SectionType.VIDEO)) {
            //2.1提交视频播放记录
//            isFinished = handleVideoRecord(userId, dto);
            isFirstFinished = handleVideoRecordV2(userId, dto);
        } else {
            //2.2 提交考试记录
            isFirstFinished = handleExamRecord(userId, dto);
        }
        //3.处理课表数据
        if (!isFirstFinished) { //如果本小节不是第一次学完，不用处理课表数据。课表数据中的更改利用延迟队列来完成。
            return;
        }
        //本小节第一次学完
//        handleLessonData(dto, isFirstFinished);
        handleLessonDataV2(dto);
    }

    //处理视频播放记录 延迟队列 + 缓存版本
    private boolean handleVideoRecordV2(Long userId, LearningRecordFormDTO dto) {
        //1.查询旧的播放记录 learning_record 条件lesson_id 和 userId
        LearningRecord learningRecord = queryOldRecordV2(dto.getLessonId(), dto.getSectionId());

        //2.判断是否存在
        if (learningRecord == null) {
            //3.如果不存在则新增学习记录。第一次学习，只创建一条数据库记录即可，不保存缓存。
            //1.将dto转换po
            LearningRecord record = BeanUtils.copyBean(dto, LearningRecord.class);
            record.setUserId(userId);
            //2.保存学习记录learning_record
            boolean result = this.save(record);
            if (!result) {
                throw new DbException("新增学习记录失败");
            }
            return false;//代表本小节没有学完
        }
        //4.如果存在则更新学习记录 learning_record 更新coment、fishied、finish_time字段
        //判断本小节是否是第一次学完
        boolean isFirstFinished = !learningRecord.getFinished() && dto.getMoment() * 2 >= dto.getDuration();
        if (!isFirstFinished) {//本小节没有第一次学完
            LearningRecord record = new LearningRecord();
            record.setLessonId(dto.getLessonId());
            record.setSectionId(dto.getSectionId());
            record.setId((learningRecord.getId()));
            record.setMoment(dto.getMoment());
            record.setFinished(learningRecord.getFinished());
            taskHandler.addLearningRecordTask(record);
            return false;
        }
        //本小节是第一次学完
        //5.更新学习记录
        boolean update = this.lambdaUpdate()
                .set(LearningRecord::getMoment, dto.getMoment())
                .set(LearningRecord::getFinished, true)
                .set(LearningRecord::getFinishTime, dto.getCommitTime())
                .eq(LearningRecord::getId, learningRecord.getId())
                .update();
        if (!update) {
            throw new DbException("更新视频学习记录失败");
        }
        //6.清理Redis缓存
        taskHandler.cleanRecordCache(dto.getLessonId(), dto.getSectionId());
        return true;
    }
    //查询旧的学习记录，缓存没有就查询数据库并放入缓存中返回。数据库没有返回空
    private LearningRecord queryOldRecordV2(Long lessonId, Long sectionId) {
        //1.查询缓存
        LearningRecord cache = taskHandler.readRecordCache(lessonId, sectionId);
        //2.如果命中直接返回
        if (cache != null) {
            return cache;
        }
        //3.如果未命中, 查询db
        LearningRecord dbRecord = this.lambdaQuery()
                .eq(LearningRecord::getLessonId, lessonId)//lessonId就相当于userId和courseId
                .eq(LearningRecord::getSectionId, sectionId)
                .one();
        //数据库查询不到，这块讲义没有写
        if (dbRecord == null) {
            return null;
        }
        //4.放入缓存
        taskHandler.writeRecordCache(dbRecord);
        return dbRecord;
    }

    //处理课表相关数据 延迟队列 + 缓存版本
    private void handleLessonDataV2(LearningRecordFormDTO dto) {
        //1.查询课表 learning_lesson 条件lesson_id 主键
        LearningLesson lesson = lessonService.getById(dto.getLessonId());
        if (lesson == null) {
            throw new BizIllegalException("课表不存在");
        }
        //2.判断是否是第一次学完 isFinished是不是true
        boolean allFinished = false;
        //3.远程调用课程服务 得到课程信息  小节总数
        CourseFullInfoDTO cinfo = courseClient.getCourseInfoById(lesson.getCourseId(), false, false);
        if (cinfo == null) {
            throw new BizIllegalException("课程不存在");
        }
        Integer sectionNum = cinfo.getSectionNum();

        //4.如果isFinished为true 本小节是第一次学完 判断该用户对该课程下全部小节是否学完
        Integer learnedSections = lesson.getLearnedSections();//当前用户对课程的已学小基数
        allFinished = learnedSections + 1 >= sectionNum;

        //5.更新课表数据
        boolean update = lessonService.lambdaUpdate()
                .set(lesson.getStatus() == LessonStatus.NOT_BEGIN, LearningLesson::getStatus, LessonStatus.LEARNING)
                .set(allFinished, LearningLesson::getStatus, LessonStatus.FINISHED)
                .set(LearningLesson::getLatestSectionId, dto.getSectionId())
                .set(LearningLesson::getLatestLearnTime, dto.getCommitTime())
//                .set(isFinished, LearningLesson::getLearnedSections, lesson.getLearnedSections() + 1)
                .setSql("learned_sections = learned_sections + 1")
                .eq(LearningLesson::getId, lesson.getId())
                .update();
    }

    //处理课表相关数据
    private void handleLessonData(LearningRecordFormDTO dto, boolean isFinished) {
        //1.查询课表 learning_lesson 条件lesson_id 主键
        LearningLesson lesson = lessonService.getById(dto.getLessonId());
        if (lesson == null) {
            throw new BizIllegalException("课表不存在");
        }
        //2.判断是否是第一次学完 isFinished是不是true
        boolean allFinished = false;
        if (isFinished) {
            //3.远程调用课程服务 得到课程信息  小节总数
            CourseFullInfoDTO cinfo = courseClient.getCourseInfoById(lesson.getCourseId(), false, false);
            if (cinfo == null) {
                throw new BizIllegalException("课程不存在");
            }
            Integer sectionNum = cinfo.getSectionNum();

            //4.如果isFinished为true 本小节是第一次学完 判断该用户对该课程下全部小节是否学完
            Integer learnedSections = lesson.getLearnedSections();//当前用户对课程的已学小基数
            allFinished = learnedSections + 1 >= sectionNum;
        }
        //5.更新课表数据
        boolean update = lessonService.lambdaUpdate()
                .set(lesson.getStatus() == LessonStatus.NOT_BEGIN, LearningLesson::getStatus, LessonStatus.LEARNING)
                .set(allFinished, LearningLesson::getStatus, LessonStatus.FINISHED)
                .set(LearningLesson::getLatestSectionId, dto.getSectionId())
                .set(LearningLesson::getLatestLearnTime, dto.getCommitTime())
//                .set(isFinished, LearningLesson::getLearnedSections, lesson.getLearnedSections() + 1)
                .setSql(isFinished, "learned_sections = learned_sections + 1")
                .eq(LearningLesson::getId, lesson.getId())
                .update();
    }

    //处理视频播放记录
    private boolean handleVideoRecord(Long userId, LearningRecordFormDTO dto) {
        //1.查询旧的播放记录 learning_record 条件lesson_id 和 userId
        LearningRecord learningRecord = this.lambdaQuery()
                .eq(LearningRecord::getUserId, userId)
                .eq(LearningRecord::getLessonId, dto.getLessonId())
                .eq(LearningRecord::getSectionId, dto.getSectionId())
                .one();
        //2.判断是否存在
        if (learningRecord == null) {
            //3.如果不存在则新增学习记录
            //1.将dto转换po
            LearningRecord record = BeanUtils.copyBean(dto, LearningRecord.class);
            record.setUserId(userId);
            //2.保存学习记录learning_record
            boolean result = this.save(record);
            if (!result) {
                throw new DbException("新增考试记录失败");
            }
            return false;//代表本小节没有学完
        }
        //4.如果存在则更新学习记录 learning_record 更新coment、fishied、finish_time字段
        //判断本小节是否是第一次学完
        boolean isFirstFinished = !learningRecord.getFinished() && dto.getMoment() * 2 >= dto.getDuration();
        boolean update = this.lambdaUpdate()
                .set(LearningRecord::getMoment, dto.getMoment())
                .set(isFirstFinished, LearningRecord::getFinished, true)
                .set(isFirstFinished, LearningRecord::getFinishTime, dto.getCommitTime())
                .eq(LearningRecord::getId, learningRecord.getId())
                .update();
        if (!update) {
            throw new DbException("更新视频学习记录失败");
        }
        return isFirstFinished;
    }

    //处理考试记录
    private boolean handleExamRecord(Long userId, LearningRecordFormDTO dto) {
        //1.将dto转换po
        LearningRecord record = BeanUtils.copyBean(dto, LearningRecord.class);
        record.setUserId(userId);
        record.setFinished(true);//提交考试记录 代表本小节已学完
        record.setFinishTime(dto.getCommitTime());
        //2.保存学习记录learning_record
        boolean result = this.save(record);
        if (!result) {
            throw new DbException("新增考试记录失败");
        }
        return true;
    }


}
