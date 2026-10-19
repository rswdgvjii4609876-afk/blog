package com.example.blog.domain;

import jakarta.persistence.*;

/** Schema mappings for community data. CommunityService owns transactional JDBC writes. */
public final class CommunityData {
    private CommunityData() { }
    @Entity @Table(name="site_settings")
    public static class Settings {
        @Id private int id;
        @Column(nullable=false,length=160) private String welcome;
        @Version private long version;
        protected Settings() { }
    }
    @Entity @Table(name="mood_entries", uniqueConstraints=@UniqueConstraint(columnNames={"entry_date","reader_id"}))
    public static class MoodEntry {
        @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
        @Column(name="entry_date",nullable=false) private java.time.LocalDate entryDate;
        @Column(name="reader_id",nullable=false,length=36) private String readerId;
        @Column(nullable=false,length=20) private String mood;
        protected MoodEntry() { }
    }
    @Entity @Table(name="guest_messages")
    public static class GuestMessage {
        @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
        @Column(nullable=false,length=40) private String nickname;
        @Column(nullable=false,length=2000) private String body;
        @Column(length=2000) private String reply;
        @Column(nullable=false,length=20) private String status;
        @Column(name="created_at",nullable=false) private java.time.Instant createdAt;
        @Version private long version;
        protected GuestMessage() { }
    }
    @Entity @Table(name="reader_polls")
    public static class ReaderPoll {
        @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
        @Column(nullable=false,length=160) private String question;
        @Column(nullable=false,length=2000) private String options;
        @Column(name="active_slot",unique=true) private Integer activeSlot;
        @Column(name="created_at",nullable=false) private java.time.Instant createdAt;
        @Version private long version;
        protected ReaderPoll() { }
    }
    @Entity @Table(name="poll_votes", uniqueConstraints=@UniqueConstraint(columnNames={"poll_id","reader_id"}))
    public static class PollVote {
        @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
        @Column(name="poll_id",nullable=false) private Long pollId;
        @Column(name="reader_id",nullable=false,length=36) private String readerId;
        @Column(name="option_index",nullable=false) private int optionIndex;
        protected PollVote() { }
    }
}
