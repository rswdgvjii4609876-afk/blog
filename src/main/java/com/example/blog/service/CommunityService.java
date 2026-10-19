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
    public record Poll(long id, String question, List<Option> options, long votes, Integer ownChoice, long version, boolean open) { }
    public record Home(Settings settings, List<MoodCount> moods, Mood ownMood, long moodTotal,
                       Poll poll, List<Message> messages, long messageCount, int messagePage, int messagePages) { }
    public record Dashboard(long posts, long published, long pendingComments, long pendingMessages,
                            long ratings, Double average, Home home, List<Poll> polls) { }
    private final JdbcTemplate jdbc;
    public CommunityService(JdbcTemplate jdbc) { this.jdbc=jdbc; }
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
        var active=jdbc.queryForList("select id from reader_polls where active_slot=1",Long.class);
        return new Home(settings(),moods,own.isEmpty()?null:Mood.valueOf(own.getFirst()),
            counts.values().stream().mapToLong(Long::longValue).sum(),active.isEmpty()?null:poll(active.getFirst(),reader),
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
    @Transactional public void createPoll(String question,String optionLines) {
        String title=text(question,160);
        var options=Arrays.stream(text(optionLines,1000).split("\\R")).map(String::strip).filter(s->!s.isEmpty()).toList();
        if(options.size()<2 || options.size()>6 || new HashSet<>(options).size()!=options.size() || options.stream().anyMatch(s->s.length()>80)) throw error(HttpStatus.BAD_REQUEST);
        lockSettings();
        if(count("select count(*) from reader_polls where active_slot=1")>0) throw error(HttpStatus.CONFLICT);
        jdbc.update("insert into reader_polls(question,options,active_slot,created_at,version) values(?,?,1,?,0)",title,String.join("\n",options),OffsetDateTime.now(ZoneOffset.UTC));
    }
    @Transactional public void closePoll(long id,long version) {
        lockSettings();
        if(jdbc.update("update reader_polls set active_slot=null,version=version+1 where id=? and version=? and active_slot=1",id,version)!=1) throw error(HttpStatus.CONFLICT);
    }
    @Transactional public void vote(long pollId,String reader,int option) {
        reader(reader);
        var rows=jdbc.queryForList("select options from reader_polls where id=? and active_slot=1 for update",String.class,pollId);
        if(rows.isEmpty()) throw error(HttpStatus.CONFLICT);
        if(option<0 || option>=rows.getFirst().split("\n").length) throw error(HttpStatus.BAD_REQUEST);
        int updated=jdbc.update("update poll_votes set option_index=? where poll_id=? and reader_id=?",option,pollId,reader);
        if(updated==0) jdbc.update("insert into poll_votes(poll_id,reader_id,option_index) values(?,?,?)",pollId,reader,option);
    }
    public Poll poll(long id,String reader) {
        var own=reader==null ? List.<Integer>of() : jdbc.queryForList("select option_index from poll_votes where poll_id=? and reader_id=?",Integer.class,id,reader);
        Map<Integer,Long> counts=new HashMap<>();
        jdbc.query("select option_index,count(*) from poll_votes where poll_id=? group by option_index",r->{counts.put(r.getInt(1),r.getLong(2));},id);
        return jdbc.queryForObject("select * from reader_polls where id=?",(r,n)->{
            String[] labels=r.getString("options").split("\n"); var options=new ArrayList<Option>();
            for(int i=0;i<labels.length;i++) options.add(new Option(i,labels[i],counts.getOrDefault(i,0L)));
            return new Poll(id,r.getString("question"),options,counts.values().stream().mapToLong(Long::longValue).sum(),
                own.isEmpty()?null:own.getFirst(),r.getLong("version"),r.getObject("active_slot")!=null);
        },id);
    }
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
