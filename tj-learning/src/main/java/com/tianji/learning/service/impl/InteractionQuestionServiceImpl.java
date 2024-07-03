package com.tianji.learning.service.impl;

import cn.hutool.core.lang.hash.Hash;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ser.impl.ReadOnlyClassToSerializerMap;
import com.tianji.api.cache.CategoryCache;
import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.client.search.SearchClient;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.course.CataSimpleInfoDTO;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.domain.query.QuestionAdminPageQuery;
import com.tianji.learning.domain.query.QuestionPageQuery;
import com.tianji.learning.domain.vo.QuestionAdminVO;
import com.tianji.learning.domain.vo.QuestionVO;
import com.tianji.learning.mapper.InteractionQuestionMapper;
import com.tianji.learning.service.IInteractionQuestionService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.learning.service.IInteractionReplyService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * <p>
 * 互动提问的问题表 服务实现类
 * </p>
 *
 * @author mirac
 * @since 2024-07-01
 */
@Service
@AllArgsConstructor
public class InteractionQuestionServiceImpl extends ServiceImpl<InteractionQuestionMapper, InteractionQuestion> implements IInteractionQuestionService {

    private final IInteractionReplyService replyService;
    private final UserClient userClient;
    private final SearchClient searchClient;
    private final CourseClient courseClient;
    private final CatalogueClient catalogueClient;
    private final CategoryCache categoryCache;

    @Override
    public void saveQuestion(QuestionFormDTO dto) {
        //1.获取当前登录用户id
        Long userId = UserContext.getUser();
        //2.dto转po
        InteractionQuestion question = BeanUtils.copyBean(dto, InteractionQuestion.class);
        question.setUserId(userId);
        //3.保存
        this.save(question);
    }

    @Override
    public void updateQuestion(Long id, QuestionFormDTO dto) {
        //1.校验
        if (StringUtils.isBlank(dto.getTitle())
                || StringUtils.isBlank(dto.getDescription())
                || dto.getAnonymity() == null) {
            throw new BadRequestException("非法参数");
        }
        //2.校验id
        InteractionQuestion question = this.getById(id);
        if (question == null) {
            throw new BadRequestException("非法参数");
        }
        //修改只能修改自己的互动问题
        Long userId = UserContext.getUser();
        if (!userId.equals(question.getUserId())) {
            throw new BadRequestException("不能修改别人的互动问题");
        }
        //2.dto转po
        question.setTitle(dto.getTitle());
        question.setDescription(dto.getDescription());
        question.setAnonymity(dto.getAnonymity());
        //3.修改
        this.updateById(question);
    }

    @Override
    public PageDTO<QuestionVO> queryQuestionPage(QuestionPageQuery query) {
        //1.校验
        if (query.getCourseId() == null) {
            throw new BadRequestException("课程id不能为空");
        }
        //2.获取登录用户id
        Long userId = UserContext.getUser();
        //3.分页查询互动问题interaction_question  条件：courseId onlyMine为true才会加userId 小节id不为空 hidden为false 分页查询
        Page<InteractionQuestion> page = this.lambdaQuery()
                .select(InteractionQuestion.class, new Predicate<TableFieldInfo>() {
                    @Override
                    public boolean test(TableFieldInfo tableFieldInfo) {
                        return !tableFieldInfo.getProperty().equals("description");//指定 不查的字段 这个字段数据量太大了
                    }
                })
                .eq(InteractionQuestion::getCourseId, query.getCourseId())
                .eq(query.getOnlyMine(), InteractionQuestion::getUserId, userId)
                .eq(query.getSectionId() != null, InteractionQuestion::getSectionId, query.getSectionId())
                .eq(InteractionQuestion::getHidden, false)
                .page(query.toMpPageDefaultSortByCreateTimeDesc());
        List<InteractionQuestion> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }
        //4.根据最新回答id 批量查询回答信息Interaction_reply 条件
//        Set<Long> latestAnswerIds = records.stream()
//                .filter(c -> c.getLatestAnswerId() != null)
//                .map(InteractionQuestion::getLatestAnswerId)
//                .collect(Collectors.toSet());
        Set<Long> latestAnswerIds = new HashSet<>();//互动问题的 最新回答id集合
        Set<Long> userIds = new HashSet<>();//互动问题的用户id集合
        for (InteractionQuestion record : records) {
            if (!record.getAnonymity()) {
                userIds.add(record.getUserId());//添加提问的用户id
            }
            if (record.getLatestAnswerId() != null) {
                latestAnswerIds.add(record.getLatestAnswerId());
            }
        }
        Map<Long, InteractionReply> replyMap = new HashMap<>();
        if (CollUtils.isNotEmpty(latestAnswerIds)) {
//            List<InteractionReply> replyList = replyService.listByIds(latestAnswerIds);
            List<InteractionReply> replyList = replyService.list(Wrappers.<InteractionReply>lambdaQuery()
                    .in(InteractionReply::getId, latestAnswerIds)
                    .eq(InteractionReply::getHidden, false));
            for (InteractionReply reply : replyList) {
                if (!reply.getAnonymity()) {//如果用户是匿名提问，则不显示用户名和头像
                    userIds.add(reply.getUserId());//将最新回答的用户id 存入userIds
                }
                replyMap.put(reply.getId(), reply);
            }
        }

        //5.远程调用用户服务 获取用户信息 批量
        List<UserDTO> userDTOS = userClient.queryUserByIds(userIds);
        Map<Long, UserDTO> userDTOMap = userDTOS.stream().collect(Collectors.toMap(UserDTO::getId, c -> c));

        //6.封装vo返回
        ArrayList<QuestionVO> voList = new ArrayList<>();
        for (InteractionQuestion record : records) {
            QuestionVO vo = BeanUtils.copyBean(record, QuestionVO.class);
            if (!vo.getAnonymity()) {
                UserDTO userDTO = userDTOMap.get(record.getUserId());
                if (userDTO != null) {
                    vo.setUserName(userDTO.getName());
                    vo.setUserIcon(userDTO.getIcon());
                }
            }
            InteractionReply reply = replyMap.get(record.getLatestAnswerId());
            if (reply != null) {
                if (!reply.getAnonymity()) {
                    UserDTO userDTO = userDTOMap.get(reply.getUserId());
                    if (userDTO != null) {
                        vo.setLatestReplyUser(userDTO.getName());
                    }
                }
                vo.setLatestReplyContent(reply.getContent());
            }
            voList.add(vo);
        }
        return PageDTO.of(page, voList);
    }

    @Override
    public QuestionVO queryQuestionById(Long id) {
        //1.校验
        if (id == null) {
            throw new BadRequestException("非法参数");
        }
        //2.查询互动问题表 按主键查询
        InteractionQuestion question = this.getById(id);
        if (question == null) {
            throw new BadRequestException("问题不存在");
        }
        //3.如果该问题管理员设置了隐藏 返回空
        if (question.getHidden()) {
            return null;
        }
        //4.封装vo返回
        QuestionVO questionVO = BeanUtils.copyBean(question, QuestionVO.class);

        //4.如果用户是匿名提问，不用查询提问者名称和头像
        if (!question.getAnonymity()) {
            //调用用户服务
            UserDTO userDTO = userClient.queryUserById(question.getUserId());
            if (userDTO != null) {
                questionVO.setUserName(userDTO.getName());
                questionVO.setUserIcon(userDTO.getIcon());
            }
        }
        return questionVO;
    }

    @Override
    public PageDTO<QuestionAdminVO> queryQuestionAdminVOPage(QuestionAdminPageQuery query) {
        //0.如果用户传了课程的名称参数 则从es中获取该名称对应的课程id。因为互动问题表没有课程名称，不能直接根据课程名称进行模糊查询。
        String courseName = query.getCourseName();
        List<Long> cids = null;
        if (StringUtils.isNotBlank(courseName)) {
            cids = searchClient.queryCoursesIdByName(courseName);//通过feign远程调用搜索服务，从es中搜索该关键字对应的课程id
            if (CollUtils.isEmpty(cids)) {
                return PageDTO.empty(0L, 0L);
            }
        }
        //1.查询互动问题表 条件：前端传条件了就添加条件 分页：排序按照提问的时间倒叙
        Page<InteractionQuestion> page = this.lambdaQuery()
                .in(CollUtils.isNotEmpty(cids), InteractionQuestion::getCourseId, cids)
                .eq(query.getStatus() != null, InteractionQuestion::getStatus, query.getStatus())
                .between(query.getBeginTime() != null && query.getEndTime() != null,
                        InteractionQuestion::getCreateTime, query.getBeginTime(), query.getEndTime())
                .page(query.toMpPageDefaultSortByCreateTimeDesc());
        List<InteractionQuestion> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(0L, 0L);
        }
        Set<Long> uids = new HashSet<>();//用户id集合
        Set<Long> courseids = new HashSet<>();//课程id结合
        Set<Long> chapterAndSectionIds = new HashSet<>();//章和节的id集合
        for (InteractionQuestion record : records) {
            uids.add(record.getUserId());
            courseids.add(record.getCourseId());
            chapterAndSectionIds.add(record.getChapterId());//章id
            chapterAndSectionIds.add(record.getSectionId());//节id
        }
        //2.远程调用用户服务 获取用户信息
        List<UserDTO> userDTOS = userClient.queryUserByIds(uids);
        if (CollUtils.isEmpty(userDTOS)) {
            throw new BizIllegalException("用户不存在");
        }
        Map<Long, UserDTO> userDTOMap = userDTOS.stream().collect(Collectors.toMap(UserDTO::getId, c -> c));
        //3.远程调用课程服务 获取课程信息
        List<CourseSimpleInfoDTO> cinfos = courseClient.getSimpleInfoList(courseids);
        if (CollUtils.isEmpty(cinfos)) {
            throw new BizIllegalException("课程不存在");
        }
        Map<Long, CourseSimpleInfoDTO> cinfoDTOMap = cinfos.stream().collect(Collectors.toMap(CourseSimpleInfoDTO::getId, c -> c));
        //4.远程调用课程服务 获取章节/目录信息
        List<CataSimpleInfoDTO> cataSimpleInfoDTOS = catalogueClient.batchQueryCatalogue(chapterAndSectionIds);
        if (CollUtils.isEmpty(cataSimpleInfoDTOS)) {
            throw new BizIllegalException("章节信息不存在");
        }
        Map<Long, String> cataInfoDTO = cataSimpleInfoDTOS.stream().collect(Collectors.toMap(CataSimpleInfoDTO::getId, c -> c.getName()));
        //5.获取分类信息 category表

        //6.封装vo返回
        ArrayList<QuestionAdminVO> voList = new ArrayList<>();
        for (InteractionQuestion record : records) {
            QuestionAdminVO adminVO = BeanUtils.copyBean(record, QuestionAdminVO.class);

            UserDTO userDTO = userDTOMap.get(record.getUserId());
            if (userDTO != null) {
                adminVO.setUserName(userDTO.getName());
            }

            CourseSimpleInfoDTO cinfoDTO = cinfoDTOMap.get(record.getCourseId());
            if (cinfoDTO != null) {
                adminVO.setCourseName(cinfoDTO.getName());
                List<Long> categoryIds = cinfoDTO.getCategoryIds();
                //获取课程一、二、三级分类
                String categoryNames = categoryCache.getCategoryNames(List.of(1001L, 2002L, 3007L));
                adminVO.setCategoryName(categoryNames);//三级分类名称，拼接字段
            }

            adminVO.setChapterName(cataInfoDTO.get(record.getChapterId()));//章名称
            adminVO.setSectionName(cataInfoDTO.get(record.getSectionId()));

            voList.add(adminVO);
        }
        return PageDTO.of(page, voList);
    }
}
