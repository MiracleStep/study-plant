package com.tianji.learning;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.domain.vo.LearningPlanPageVO;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.enums.PlanStatus;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.service.ILearningLessonService;
import com.tianji.learning.service.impl.LearningLessonServiceImpl;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class LearningLessonServiceImplTest {

    @Autowired
    private ILearningLessonService lessonService;

    @Test
    public void testQueryMyPlans() {
        QueryWrapper<LearningLesson> wrapper = new QueryWrapper<>();
        wrapper.select("sum(week_freq) as planTotal");
        wrapper.eq("user_id", 2);
        wrapper.in("status", LessonStatus.NOT_BEGIN, LessonStatus.LEARNING);
        wrapper.eq("plan_status", PlanStatus.PLAN_RUNNING);
        Map<String, Object> map = lessonService.getMap(wrapper);// {planTotal: 7}
        System.out.println(map);
        BigDecimal planTotal = (BigDecimal) map.get("planTotal");
        System.out.println(planTotal.intValue());
    }

    @Autowired
    private LearningRecordMapper recordMapper;

    @Test
    public void test2() {
        QueryWrapper<LearningRecord> rWrapper = new QueryWrapper<>();
        rWrapper.select("lesson_id AS id, COUNT(1) AS num");
        rWrapper.eq("user_id", 2);
        rWrapper.eq("finished", true);
        //'2024-06-25 14:20:31' and  '2024-06-29 14:20:31'
        rWrapper.between("finish_time", "2024-06-25 14:20:31", "2024-06-29 14:20:31");
        rWrapper.groupBy("lesson_id");
//        List<LearningRecord> learningRecords = recordMapper.selectList(rWrapper);
        List<Map<String, Object>> maps = recordMapper.selectMaps(rWrapper);

        List<Map<String, Object>> learningRecords = recordMapper.selectMaps(rWrapper); //[{num=2, id=1806572663546937345},{...}]
        Map<Long, Integer> weekFinishedNumMap = learningRecords.stream().collect(Collectors.toMap(
                item -> Long.valueOf(item.get("id").toString()),
                item -> Integer.valueOf(item.get("num").toString())
        ));
        System.out.println(weekFinishedNumMap);
    }
}
