package com.example.blog.service;

import java.time.*;
import java.util.*;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.DependsOn;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
@DependsOn("entityManagerFactory")
@Transactional(readOnly=true)
public class CommunityService {
    public static final String DEFAULT_WELCOME = "欢迎来到我的博客，准备好了解我了吗？";
    public enum Mood {
        HAPPY("开心", "😊"), CALM("平静", "🌿"), TIRED("疲惫", "☕"), LOW("低落", "🌧"), HOPEFUL("期待", "🌱");
        public final String label; public final String icon;
        Mood(String label, String icon) { this.label=label; this.icon=icon; }
        public String getLabel() { return label; } public String getIcon() { return icon; }
    }
    public record Settings(String welcome, long version) { }
    public record MoodCount(Mood mood, long count) { }
    public record Message(long id, String nickname, String body, String reply, String status, Instant createdAt, long version) { }
    public record Option(int index, String label, long votes) { }
    public static final int OTHER_OPTION = 6;
    public record Poll(long id, String question, List<Option> options, long votes, Integer ownChoice,
                       long version, boolean open, Instant endsAt, boolean otherEnabled, boolean current, Instant serverNow) { }
    public record OtherResponse(long id, String body) { }
    public record Home(Settings settings, List<MoodCount> moods, Mood ownMood, long moodTotal,
                       Poll poll, List<Message> messages, long messageCount, int messagePage, int messagePages) { }
    public record Dashboard(long posts, long published, long pendingComments, long pendingMessages,
                            long ratings, Double average, Home home, List<Poll> polls) { }
    private final JdbcTemplate jdbc;
    private final Clock clock;
    public CommunityService(JdbcTemplate jdbc, Clock clock) { this.jdbc=jdbc; this.clock=clock; }
    @PostConstruct void seedSettings() {
        // V3 seeds production. Hibernate-created isolated test schemas also need the singleton.
        try { jdbc.update("insert into site_settings(id,welcome,version) values(1,?,0)", DEFAULT_WELCOME); }
        catch (DuplicateKeyException exists) { /* Never reset the author's configured welcome. */ }
    }
    public Settings settings() {
        return jdbc.queryForObject("select welcome,version from site_settings where id=1",
            (r,n)->new Settings(r.getString(1),r.getLong(2)));
    }
    public Home home(String reader, int page) {
        page(page);
        LocalDate date = today();
        var counts = new EnumMap<Mood,Long>(Mood.class);
        jdbc.query("select mood,count(*) from mood_entries where entry_date=? group by mood", r->{counts.put(Mood.valueOf(r.getString(1)),r.getLong(2));}, date);
        var moods = Arrays.stream(Mood.values()).map(m->new MoodCount(m,counts.getOrDefault(m,0L))).toList();
        var own = reader==null ? List.<String>of() : jdbc.queryForList("select mood from mood_entries where entry_date=? and reader_id=?",String.class,date,reader);
        long total = count("select count(*) from guest_messages where status='APPROVED'");
        int pages=(int)((total+9)/10);
        if(page>0 && page>=pages) throw error(HttpStatus.NOT_FOUND);
        var messages=messages("APPROVED",page);
        var latest=jdbc.queryForList("select id from reader_polls order by id desc limit 1",Long.class);
        Poll visible = latest.isEmpty() ? null : poll(latest.getFirst(),reader);
        // Old manually closed polls retain their previous hidden-home behavior.
        if (visible != null && !visible.current() && visible.endsAt() == null) visible = null;
        return new Home(settings(),moods,own.isEmpty()?null:Mood.valueOf(own.getFirst()),
            counts.values().stream().mapToLong(Long::longValue).sum(),visible,
            messages,total,page,pages);
    }
    @Transactional public void welcome(String welcome,long version) {
        String value=text(welcome,160);
        if(jdbc.update("update site_settings set welcome=?,version=version+1 where id=1 and version=?",value,version)!=1) throw error(HttpStatus.CONFLICT);
    }
    @Transactional public void mood(String reader,Mood mood) {
        reader(reader); if(mood==null) throw error(HttpStatus.BAD_REQUEST);
        lockSettings();
        LocalDate date = today();
        int updated=jdbc.update("update mood_entries set mood=? where entry_date=? and reader_id=?",mood.name(),date,reader);
        if(updated==0) jdbc.update("insert into mood_entries(entry_date,reader_id,mood) values(?,?,?)",date,reader,mood.name());
    }
    @Transactional public void leaveMessage(String nickname,String body) {
        jdbc.update("insert into guest_messages(nickname,body,status,created_at,version) values(?,?,'PENDING',?,0)",
            text(nickname,40),text(body,2000),OffsetDateTime.now(ZoneOffset.UTC));
    }
    public List<Message> messages(String status,int page) {
        page(page); status(status);
        return jdbc.query("select * from guest_messages where status=? order by created_at desc,id desc limit 10 offset ?",
            (r,n)->new Message(r.getLong("id"),r.getString("nickname"),r.getString("body"),r.getString("reply"),
                r.getString("status"),r.getObject("created_at",OffsetDateTime.class).toInstant(),r.getLong("version")),status,page*10);
    }
    public long messageCount(String status) { status(status); return count("select count(*) from guest_messages where status=?",status); }
    @Transactional public void moderate(long id,long version,String action,String reply) {
        var rows=jdbc.queryForList("select version from guest_messages where id=? for update",Long.class,id);
        if(rows.isEmpty()) throw error(HttpStatus.NOT_FOUND);
        if(rows.getFirst()!=version) throw error(HttpStatus.CONFLICT);
        switch(action) {
            case "approve" -> jdbc.update("update guest_messages set status='APPROVED',version=version+1 where id=?",id);
            case "hide" -> jdbc.update("update guest_messages set status='HIDDEN',version=version+1 where id=?",id);
            case "delete" -> jdbc.update("delete from guest_messages where id=?",id);
            case "reply" -> jdbc.update("update guest_messages set reply=?,version=version+1 where id=?",text(reply,2000),id);
            default -> throw error(HttpStatus.BAD_REQUEST);
        }
    }
    /** Compatibility for internal callers: new polls always default to 24 hours. */
    @Transactional public void createPoll(String question,String optionLines) {
        createPoll(question, Arrays.stream(text(optionLines,1000).split("\\R")).map(String::strip).filter(v->!v.isEmpty()).toList(),24,false);
    }
    @Transactional public void createPoll(String question,List<String> input,long hours,boolean otherEnabled) {
        String title=text(question,160);
        duration(hours);
        if(input==null || input.size()<2 || input.size()>6) throw error(HttpStatus.BAD_REQUEST);
        var options=input.stream().map(value->text(value,80)).toList();
        if(new HashSet<>(options).size()!=options.size() || options.stream().anyMatch(value->value.contains("\n") || value.contains("\r"))) throw error(HttpStatus.BAD_REQUEST);
        lockSettings();
        var active=jdbc.queryForList("select id from reader_polls where active_slot=1 for update",Long.class);
        if(!active.isEmpty()) {
            Poll previous=poll(active.getFirst(),null);
            if(previous.open()) throw error(HttpStatus.CONFLICT);
            jdbc.update("update reader_polls set active_slot=null,version=version+1 where id=?",previous.id());
        }
        Instant now=clock.instant();
        jdbc.update("insert into reader_polls(question,options,active_slot,created_at,version,ends_at,other_enabled) values(?,?,1,?,0,?,?)",
            title,String.join("\n",options),OffsetDateTime.ofInstant(now,ZoneOffset.UTC),OffsetDateTime.ofInstant(now.plusSeconds(hours*3600),ZoneOffset.UTC),otherEnabled);
    }
    @Transactional public void closePoll(long id,long version) {
        lockSettings();
        if(jdbc.update("update reader_polls set active_slot=null,version=version+1 where id=? and version=? and active_slot=1",id,version)!=1) throw error(HttpStatus.CONFLICT);
    }
    @Transactional public void extendPoll(long id,long version,long hours) {
        duration(hours); lockSettings();
        var rows=jdbc.queryForList("select id from reader_polls where id=? and active_slot=1 for update",Long.class,id);
        if(rows.isEmpty()) throw error(HttpStatus.CONFLICT);
        Poll current=poll(id,null);
        if(current.version()!=version || current.endsAt()==null) throw error(HttpStatus.CONFLICT);
        Instant now=clock.instant();
        Instant base=current.endsAt().isAfter(now)?current.endsAt():now;
        jdbc.update("update reader_polls set ends_at=?,version=version+1 where id=?",OffsetDateTime.ofInstant(base.plusSeconds(hours*3600),ZoneOffset.UTC),id);
    }
    @Transactional public void vote(long pollId,String reader,int option) { vote(pollId,reader,option,null); }
    @Transactional public void vote(long pollId,String reader,int option,String otherText) {
        reader(reader);
        var rows=jdbc.queryForList("select id from reader_polls where id=? and active_slot=1 for update",Long.class,pollId);
        if(rows.isEmpty()) throw error(HttpStatus.CONFLICT);
        // Check the server clock AFTER acquiring the lock, including at the exact deadline.
        Poll current=poll(pollId,null);
        if(!current.open()) throw error(HttpStatus.CONFLICT);
        int normalOptions=current.options().size()-(current.otherEnabled()?1:0);
        boolean other=option==OTHER_OPTION;
        if(other ? !current.otherEnabled() : option<0 || option>=normalOptions) throw error(HttpStatus.BAD_REQUEST);
        String privateText=other?text(otherText,300):null;
        int stored=other?0:option;
        int updated=jdbc.update("update poll_votes set option_index=?,is_other=?,other_text=? where poll_id=? and reader_id=?",stored,other,privateText,pollId,reader);
        if(updated==0) jdbc.update("insert into poll_votes(poll_id,reader_id,option_index,is_other,other_text) values(?,?,?,?,?)",pollId,reader,stored,other,privateText);
    }
    /** Public projection intentionally contains no free-text vote responses, even the reader's own. */
    public Poll poll(long id,String reader) {
        if(count("select count(*) from reader_polls where id=?",id)==0) throw error(HttpStatus.NOT_FOUND);
        var own=reader==null ? List.<Integer>of() : jdbc.queryForList("select case when is_other then 6 else option_index end from poll_votes where poll_id=? and reader_id=?",Integer.class,id,reader);
        Map<Integer,Long> counts=new HashMap<>();
        jdbc.query("select case when is_other then 6 else option_index end as choice,count(*) from poll_votes where poll_id=? group by choice",r->{counts.put(r.getInt(1),r.getLong(2));},id);
        return jdbc.queryForObject("select * from reader_polls where id=?",(r,n)->{
            String[] labels=r.getString("options").split("\n"); var options=new ArrayList<Option>();
            for(int i=0;i<labels.length;i++) options.add(new Option(i,labels[i],counts.getOrDefault(i,0L)));
            boolean enabled=r.getBoolean("other_enabled");
            if(enabled) options.add(new Option(OTHER_OPTION,"其他",counts.getOrDefault(OTHER_OPTION,0L)));
            var end=r.getObject("ends_at",OffsetDateTime.class);
            Instant endsAt=end==null?null:end.toInstant();
            Instant now=clock.instant();
            boolean current=r.getObject("active_slot")!=null;
            return new Poll(id,r.getString("question"),options,counts.values().stream().mapToLong(Long::longValue).sum(),
                own.isEmpty()?null:own.getFirst(),r.getLong("version"),current && (endsAt==null || now.isBefore(endsAt)),endsAt,enabled,current,now);
        },id);
    }
    public List<OtherResponse> otherResponses(long id,int page) {
        page(page);
        if(count("select count(*) from reader_polls where id=?",id)==0) throw error(HttpStatus.NOT_FOUND);
        return jdbc.query("select id,other_text from poll_votes where poll_id=? and is_other=true order by id desc limit 10 offset ?",
            (r,n)->new OtherResponse(r.getLong(1),r.getString(2)),id,page*10);
    }
    public long otherResponseCount(long id) { return count("select count(*) from poll_votes where poll_id=? and is_other=true",id); }
    private void duration(long hours) { if(hours<1 || hours>8760) throw error(HttpStatus.BAD_REQUEST); }
    public Dashboard dashboard() {
        return new Dashboard(count("select count(*) from posts"),count("select count(*) from posts where status='PUBLISHED'"),
            count("select count(*) from reader_comments where status='PENDING'"),messageCount("PENDING"),
            count("select count(*) from reader_ratings"),jdbc.queryForObject("select avg(cast(stars as double)) from reader_ratings",Double.class),
            home(null,0),jdbc.queryForList("select id from reader_polls order by id desc limit 10",Long.class).stream().map(id->poll(id,null)).toList());
    }
    private void lockSettings() { jdbc.queryForObject("select id from site_settings where id=1 for update",Integer.class); }
    private long count(String sql,Object...args) { return Objects.requireNonNull(jdbc.queryForObject(sql,Long.class,args)); }
    private LocalDate today() { return LocalDate.now(ZoneId.of("Asia/Shanghai")); }
    private void reader(String id) { if(id==null || !id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) throw error(HttpStatus.BAD_REQUEST); }
    private void page(int page) { if(page<0 || (long)page*10>Integer.MAX_VALUE) throw error(HttpStatus.BAD_REQUEST); }
    private void status(String status) { if(!Set.of("PENDING","APPROVED","HIDDEN").contains(status)) throw error(HttpStatus.BAD_REQUEST); }
    private String text(String text,int max) { if(text==null || text.isBlank() || text.strip().length()>max) throw error(HttpStatus.BAD_REQUEST); return text.strip(); }
    private ResponseStatusException error(HttpStatus status) { return new ResponseStatusException(status); }
}
