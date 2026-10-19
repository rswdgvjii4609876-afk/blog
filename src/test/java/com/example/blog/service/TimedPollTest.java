package com.example.blog.service;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest @ActiveProfiles("test") @Transactional
class TimedPollTest {
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired CommunityService service;
    private final Instant start=Instant.parse("2026-10-20T00:00:00Z");
    private CommunityService at(Instant time) { return new CommunityService(jdbc,Clock.fixed(time,ZoneOffset.UTC)); }
    private String reader() { return UUID.randomUUID().toString(); }
    @Test void exactDeadlineRejectsWritesWithoutChangingExistingVoteAndRetainsResults() {
        var before=at(start); before.createPoll("限时",List.of("甲","乙"),24,false);
        var poll=before.home(null,0).poll(); String reader=reader();
        assertThat(poll.endsAt()).isEqualTo(start.plusSeconds(86400));
        at(poll.endsAt().minusNanos(1)).vote(poll.id(),reader,0);
        var ended=at(poll.endsAt());
        assertThatThrownBy(()->ended.vote(poll.id(),reader,1)).hasMessageContaining("409");
        var result=ended.home(reader,0).poll();
        assertThat(result.open()).isFalse(); assertThat(result.ownChoice()).isZero(); assertThat(result.votes()).isEqualTo(1);
    }
    @Test void extensionUsesOriginalDeadlineBeforeExpiryAndCurrentTimeAfterExpiry() {
        var initial=at(start); initial.createPoll("延期",List.of("甲","乙"),24,false);
        var poll=initial.home(null,0).poll();
        initial.extendPoll(poll.id(),0,24);
        assertThat(initial.poll(poll.id(),null).endsAt()).isEqualTo(start.plusSeconds(48*3600));
        assertThatThrownBy(()->initial.extendPoll(poll.id(),0,24)).hasMessageContaining("409");
        var later=at(start.plusSeconds(72*3600)); later.extendPoll(poll.id(),1,2);
        assertThat(later.poll(poll.id(),null).endsAt()).isEqualTo(start.plusSeconds(74*3600));
        later.vote(poll.id(),reader(),1);
    }
    @Test void expiredPollCanBeReplacedButCannotBeRevivedAfterReplacementOrManualClose() {
        var initial=at(start); initial.createPoll("旧",List.of("甲","乙"),1,false); long old=initial.home(null,0).poll().id();
        var later=at(start.plusSeconds(3600)); later.createPoll("新",List.of("甲","乙"),1,false);
        assertThat(later.home(null,0).poll().id()).isNotEqualTo(old);
        assertThatThrownBy(()->later.extendPoll(old,0,1)).hasMessageContaining("409");
        var current=later.home(null,0).poll(); later.closePoll(current.id(),current.version());
        assertThatThrownBy(()->later.extendPoll(current.id(),1,1)).hasMessageContaining("409");
    }
    @Test void otherChoiceHasOneVoteAndClearsPrivateTextWhenChangedToNormal() {
        var app=at(start); app.createPoll("其他",List.of("甲","乙","丙","丁","戊","己"),1,true);
        long id=app.home(null,0).poll().id(); String reader=reader();
        assertThatThrownBy(()->app.vote(id,reader,6," ")).hasMessageContaining("400");
        assertThatThrownBy(()->app.vote(id,reader,6,"x".repeat(301))).hasMessageContaining("400");
        app.vote(id,reader,6,"<script>private</script>");
        assertThat(app.poll(id,reader).options()).hasSize(7);
        assertThat(app.poll(id,reader).votes()).isEqualTo(1);
        assertThat(app.otherResponses(id,0).getFirst().body()).isEqualTo("<script>private</script>");
        app.vote(id,reader,6,"修改后的答案");
        assertThat(app.otherResponseCount(id)).isEqualTo(1);
        app.vote(id,reader,2,"不应该保存");
        assertThat(app.poll(id,reader).votes()).isEqualTo(1);
        assertThat(app.otherResponses(id,0)).isEmpty();
        assertThat(jdbc.queryForObject("select other_text from poll_votes where poll_id=?",String.class,id)).isNull();
    }
    @Test void disabledOtherAndInvalidDurationsOptionsAreRejected() {
        var app=at(start);
        for(long hours:new long[]{0,-1,8761,Long.MAX_VALUE}) assertThatThrownBy(()->app.createPoll("题",List.of("甲","乙"),hours,false)).hasMessageContaining("400");
        for(var options:List.of(List.of("甲"),List.of("甲","甲"),List.of("甲\n乙","丙"),List.of(" ","乙")))
            assertThatThrownBy(()->app.createPoll("题",options,1,false)).hasMessageContaining("400");
        app.createPoll("题",List.of("甲","乙"),1,false); long id=app.home(null,0).poll().id();
        assertThatThrownBy(()->app.vote(id,reader(),6,"不接受")).hasMessageContaining("400");
    }
    @Test void oldPollHasNoDeadlineAndStillNeedsManualClosure() {
        jdbc.update("insert into reader_polls(question,options,active_slot,created_at,version,other_enabled) values('旧投票',?,1,?,0,false)","甲\n乙",OffsetDateTime.ofInstant(start,ZoneOffset.UTC));
        var future=at(start.plusSeconds(365L*86400)); var poll=future.home(null,0).poll();
        assertThat(poll.endsAt()).isNull(); assertThat(poll.open()).isTrue();
        future.vote(poll.id(),reader(),1);
        assertThatThrownBy(()->future.extendPoll(poll.id(),0,24)).hasMessageContaining("409");
        future.closePoll(poll.id(),0); assertThat(future.home(null,0).poll()).isNull();
    }
}
