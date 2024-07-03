package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.ReplyDTO;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.domain.query.ReplyPageQuery;
import com.tianji.learning.domain.vo.ReplyVO;
import com.tianji.learning.enums.QuestionStatus;
import com.tianji.learning.mapper.InteractionQuestionMapper;
import com.tianji.learning.mapper.InteractionReplyMapper;
import com.tianji.learning.service.IInteractionReplyService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.tianji.common.constants.Constant.DATA_FIELD_NAME_CREATE_TIME;
import static com.tianji.common.constants.Constant.DATA_FIELD_NAME_LIKED_TIME;

/**
 * <p>
 * 互动问题的回答或评论 服务实现类
 * </p>
 *
 * @author mirac
 * @since 2024-07-01
 */
@Service
@AllArgsConstructor
public class InteractionReplyServiceImpl extends ServiceImpl<InteractionReplyMapper, InteractionReply> implements IInteractionReplyService {

    private final InteractionQuestionMapper questionMapper;
    private final UserClient userClient;


    @Override
    public void saveReply(ReplyDTO dto) {
        //1.获取当前登录用户
        Long userId = UserContext.getUser();
        //2.保存回答或者评论 interaction_reply
        InteractionReply reply = BeanUtils.copyBean(dto, InteractionReply.class);
        reply.setUserId(userId);
        this.save(reply);
        //3.判断是否是回答 dto.answerId 为空则为回答
        InteractionQuestion question = questionMapper.selectById(dto.getQuestionId());
        if (dto.getAnswerId() != null) {
            //3.1如果不是回答（则是回复/评论），累加问答下的评论次数
            InteractionReply answerInfo = this.getById(dto.getAnswerId());//回答
            answerInfo.setReplyTimes(answerInfo.getReplyTimes() + 1);//评论次数累加
            this.updateById(answerInfo);
        } else {
            //3.2如果是回答 修改问题表最近一次回答id 同时累加问题表回答次数
            question.setLatestAnswerId(reply.getId());
            question.setAnswerTimes(question.getAnswerTimes() + 1);
        }
        //4.判断是否是学生提交 dto.isStudent 为 true则代表学生提交 如果是则将问题表中该问题的status字段改为未查看
        if (dto.getIsStudent()) {
            question.setStatus(QuestionStatus.UN_CHECK);
        }
        questionMapper.updateById(question);
    }

    @Override
    public PageDTO<ReplyVO> queryReplyPage(ReplyPageQuery query, boolean forAdmin) {
        // 1.问题id和回答id至少要有一个，先做参数判断
        Long questionId = query.getQuestionId();
        Long answerId = query.getAnswerId();
        if (questionId == null && answerId == null) {
            throw new BadRequestException("问题或回答id不能都为空");
        }
        // 标记当前是查询问题下的回答
        boolean isQueryAnswer = questionId != null;
        // 2.分页查询reply
        Page<InteractionReply> page = lambdaQuery()
                .eq(isQueryAnswer, InteractionReply::getQuestionId, questionId)
                .eq(InteractionReply::getAnswerId, isQueryAnswer ? 0L : answerId)
                .eq(!forAdmin, InteractionReply::getHidden, false)
                .page(query.toMpPage( // 先根据点赞数排序，点赞数相同，再按照创建时间排序
                        new OrderItem(DATA_FIELD_NAME_LIKED_TIME, false),
                        new OrderItem(DATA_FIELD_NAME_CREATE_TIME, true))
                );
        List<InteractionReply> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }
        // 3.数据处理，需要查询：提问者信息、回复目标信息、当前用户是否点赞
        Set<Long> userIds = new HashSet<>();
        Set<Long> answerIds = new HashSet<>();
        Set<Long> targetReplyIds = new HashSet<>();
        // 3.1.获取提问者id 、回复的目标id、当前回答或评论id（统计点赞信息）
        for (InteractionReply r : records) {
            if(!r.getAnonymity() || forAdmin) {
                // 非匿名
                userIds.add(r.getUserId());
            }
            targetReplyIds.add(r.getTargetReplyId());
            answerIds.add(r.getId());
        }
        // 3.2.查询目标回复，如果目标回复不是匿名，则需要查询出目标回复的用户信息
        targetReplyIds.remove(0L);
        targetReplyIds.remove(null);
        if(targetReplyIds.size() > 0) {
            List<InteractionReply> targetReplies = listByIds(targetReplyIds);
            Set<Long> targetUserIds = targetReplies.stream()
                    .filter(Predicate.not(InteractionReply::getAnonymity).or(r -> forAdmin))
                    .map(InteractionReply::getUserId)
                    .collect(Collectors.toSet());
            userIds.addAll(targetUserIds);
        }
        // 3.3.查询用户
        Map<Long, UserDTO> userMap = new HashMap<>(userIds.size());
        if(userIds.size() > 0) {
            List<UserDTO> users = userClient.queryUserByIds(userIds);
            userMap = users.stream().collect(Collectors.toMap(UserDTO::getId, u -> u));
        }
        // 3.4.查询用户点赞状态
//        Set<Long> bizLiked = remarkClient.isBizLiked(answerIds);
        // 4.处理VO
        List<ReplyVO> list = new ArrayList<>(records.size());
        for (InteractionReply r : records) {
            // 4.1.拷贝基础属性
            ReplyVO v = BeanUtils.toBean(r, ReplyVO.class);
            list.add(v);
            // 4.2.回复人信息
            if(!r.getAnonymity() || forAdmin){
                UserDTO userDTO = userMap.get(r.getUserId());
                if (userDTO != null) {
                    v.setUserIcon(userDTO.getIcon());
                    v.setUserName(userDTO.getName());
                    v.setUserType(userDTO.getType());
                }
            }
            // 4.3.如果存在评论的目标，则需要设置目标用户信息
            if(r.getTargetReplyId() != null){
                UserDTO targetUser = userMap.get(r.getTargetUserId());
                if (targetUser != null) {
                    v.setTargetUserName(targetUser.getName());
                }
            }
            // 4.4.点赞状态
//            v.setLiked(bizLiked.contains(r.getId()));
        }
        return new PageDTO<>(page.getTotal(), page.getPages(), list);
    }
}
