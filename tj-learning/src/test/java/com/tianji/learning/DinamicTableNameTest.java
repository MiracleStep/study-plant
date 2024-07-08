package com.tianji.learning;

import com.tianji.learning.domain.po.PointsBoard;
import com.tianji.learning.service.IPointsBoardService;
import com.tianji.learning.utils.TableInfoContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestExecutionListeners;

@SpringBootTest(classes = LearningApplication.class)
public class DinamicTableNameTest {

    @Autowired
    IPointsBoardService pointsBoardService;

    @Test
    public void test() {
        TableInfoContext.setInfo("points_board_9");
        PointsBoard board = new PointsBoard();
        board.setId(1L);
        board.setUserId(2L);
        board.setPoints(200);
        pointsBoardService.save(board);
    }
}
