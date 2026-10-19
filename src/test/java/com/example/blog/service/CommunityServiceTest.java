package com.example.blog.service;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest @ActiveProfiles("test") @Transactional
class CommunityServiceTest {
    @Autowired CommunityService service;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    private String reader() { return UUID.randomUUID().toString(); }
    @Test void dailyMoodUpdatesInsteadOfAddingVotesAndUsesShanghaiDay() {
        String reader=reader(); service.mood(reader,CommunityService.Mood.HAPPY); service.mood(reader,CommunityService.Mood.LOW);
        var home=service.home(reader,0);
        assertThat(home.ownMood()).isEqualTo(CommunityService.Mood.LOW);
        assertThat(home.moodTotal()).isEqualTo(1);
        assertThat(service.home(reader(),0).ownMood()).isNull();
        assertThat(jdbc.queryForObject("select entry_date from mood_entries where reader_id=?",java.time.LocalDate.class,reader))
            .isEqualTo(java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")));
        jdbc.update("update mood_entries set entry_date=entry_date-1 where reader_id=?",reader);
        assertThat(service.home(reader,0).moodTotal()).isZero();
    }
    @Test void pendingMessagesAndRepliesNeverLeakUntilApprovedAndStaleActionsFail() {
        service.leaveMessage("读者","<script>test</script>");
        var message=service.messages("PENDING",0).getFirst();
        service.moderate(message.id(),message.version(),"reply","作者回复");
        assertThat(service.home(null,0).messages()).isEmpty();
        assertThatThrownBy(()->service.moderate(message.id(),0,"approve",null)).hasMessageContaining("409");
        service.moderate(message.id(),1,"approve",null);
        assertThat(service.home(null,0).messages().getFirst().reply()).isEqualTo("作者回复");
        service.moderate(message.id(),2,"hide",null);
        assertThat(service.home(null,0).messages()).isEmpty();
        service.moderate(message.id(),3,"delete",null);
        assertThat(service.messageCount("HIDDEN")).isZero();
    }
    @Test void pollEnforcesOneActivePollValidOptionsAndOneVotePerBrowser() {
        service.createPoll("你想读什么？","学习笔记\n生活记录");
        var poll=service.home(null,0).poll();
        assertThatThrownBy(()->service.createPoll("另一个","甲\n乙")).hasMessageContaining("409");
        String reader=reader(); service.vote(poll.id(),reader,0); service.vote(poll.id(),reader,1);
        service.vote(poll.id(),reader(),0);
        var result=service.home(reader,0).poll();
        assertThat(result.votes()).isEqualTo(2); assertThat(result.ownChoice()).isEqualTo(1);
        assertThat(result.options().getFirst().votes()).isEqualTo(1);
        assertThatThrownBy(()->service.vote(poll.id(),reader,2)).hasMessageContaining("400");
        service.closePoll(poll.id(),poll.version());
        assertThat(service.home(reader,0).poll()).isNull();
        assertThatThrownBy(()->service.vote(poll.id(),reader,0)).hasMessageContaining("409");
        service.createPoll("下一次","甲\n乙");
        assertThat(service.poll(poll.id(),null).votes()).isEqualTo(2);
    }
    @Test void validationAndWelcomeVersionProtectStoredContent() {
        var settings=service.settings(); service.welcome("你好，欢迎回来",settings.version());
        assertThatThrownBy(()->service.welcome("旧窗口",settings.version())).hasMessageContaining("409");
        assertThat(service.settings().welcome()).isEqualTo("你好，欢迎回来");
        for(String options:new String[]{"只有一个","重复\n重复","x".repeat(81)+"\n乙"}) {
            assertThatThrownBy(()->service.createPoll("问题",options)).hasMessageContaining("400");
        }
        assertThatThrownBy(()->service.mood(null,CommunityService.Mood.CALM)).hasMessageContaining("400");
        assertThatThrownBy(()->service.leaveMessage(" ","内容")).hasMessageContaining("400");
        assertThatThrownBy(()->service.leaveMessage("昵称","x".repeat(2001))).hasMessageContaining("400");
        assertThatThrownBy(()->service.home(null,-1)).hasMessageContaining("400");
    }
    @Test void messageWallIsPagedAndCountsOnlyApprovedMessages() {
        for(int i=0;i<12;i++) service.leaveMessage("读者","第"+i+"条");
        for(var message:service.messages("PENDING",0)) service.moderate(message.id(),message.version(),"approve",null);
        var home=service.home(null,0); assertThat(home.messages()).hasSize(10);
        assertThat(home.messageCount()).isEqualTo(10);
        for(var message:service.messages("PENDING",0)) service.moderate(message.id(),message.version(),"approve",null);
        assertThat(service.home(null,1).messages()).hasSize(2);
    }
}
