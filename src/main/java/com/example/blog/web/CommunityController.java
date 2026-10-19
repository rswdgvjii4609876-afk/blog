package com.example.blog.web;

import com.example.blog.service.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class CommunityController {
    private final CommunityService service;
    private final ReaderWriteLimiter limiter;
    public CommunityController(CommunityService service,ReaderWriteLimiter limiter) { this.service=service; this.limiter=limiter; }
    @GetMapping("/identity") public String identity() { return "identity"; }
    @GetMapping({"/admin","/admin/overview"}) public String overview(Model model) {
        model.addAttribute("dashboard",service.dashboard()); return "admin/overview";
    }
    @PostMapping("/community/mood") public String mood(@RequestParam CommunityService.Mood mood,HttpServletRequest request,RedirectAttributes flash) {
        limiter.check(request.getRemoteAddr(),"mood",15);
        service.mood(ReaderIdentity.read(request),mood);
        flash.addFlashAttribute("moodNotice","已记下今天的心情，愿你在这里收获一点温暖。");
        return "redirect:/#mood";
    }
    @PostMapping("/community/polls/{id}/vote") public String vote(@PathVariable long id,@RequestParam int option,@RequestParam(required=false) String otherText,HttpServletRequest request,RedirectAttributes flash) {
        limiter.check(request.getRemoteAddr(),"poll",15);
        service.vote(id,ReaderIdentity.read(request),option,otherText);
        flash.addFlashAttribute("communityNotice","投票已保存，谢谢你参与！"); return "redirect:/#community";
    }
    public record PollState(long id, boolean open, java.time.Instant endsAt, java.time.Instant serverNow,
                            java.util.List<CommunityService.Option> results, Long votes) { }
    @GetMapping("/community/polls/{id}/status") @ResponseBody
    public org.springframework.http.ResponseEntity<PollState> pollStatus(@PathVariable long id,HttpServletRequest request) {
        var poll=service.poll(id,ReaderIdentity.read(request));
        boolean showResults=!poll.open() || poll.ownChoice()!=null;
        return org.springframework.http.ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
            .body(new PollState(poll.id(),poll.open(),poll.endsAt(),poll.serverNow(),
                showResults?poll.options():java.util.List.of(),showResults?poll.votes():null));
    }
    @PostMapping("/community/messages") public String message(@RequestParam String nickname,@RequestParam String body,
            @RequestParam(defaultValue="") String website,HttpServletRequest request,RedirectAttributes flash) {
        limiter.check(request.getRemoteAddr(),"wall",5);
        if(website.isEmpty()) {
            if(nickname.isBlank() || nickname.strip().length()>40 || body.isBlank() || body.strip().length()>2000) {
                flash.addFlashAttribute("communityError","请填写昵称（1～40 字）和留言（1～2000 字）。");
                flash.addFlashAttribute("wallNickname",nickname.substring(0,Math.min(40,nickname.length())));
                flash.addFlashAttribute("wallBody",body.substring(0,Math.min(2000,body.length())));
                return "redirect:/#community";
            }
            service.leaveMessage(nickname,body);
        }
        flash.addFlashAttribute("communityNotice","留言已收到，作者审核后会展示在这里。"); return "redirect:/#community";
    }
    @PostMapping("/admin/community/welcome") public String welcome(@RequestParam String welcome,@RequestParam long version,RedirectAttributes flash) {
        service.welcome(welcome,version); return done(flash);
    }
    @PostMapping("/admin/community/polls") public String createPoll(@RequestParam String question,
            @RequestParam java.util.List<String> options,@RequestParam(defaultValue="24") String duration,
            @RequestParam(required=false) Long customHours,@RequestParam(defaultValue="false") boolean otherEnabled,
            RedirectAttributes flash) {
        long hours = switch(duration) {
            case "24" -> 24; case "48" -> 48; case "72" -> 72; case "168" -> 168;
            case "custom" -> customHours == null ? 0 : customHours;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        };
        service.createPoll(question,options,hours,otherEnabled); return done(flash);
    }
    @PostMapping("/admin/community/polls/{id}/extend") public String extendPoll(@PathVariable long id,
            @RequestParam long version,@RequestParam long hours,RedirectAttributes flash) {
        service.extendPoll(id,version,hours); return done(flash);
    }
    @GetMapping("/admin/community/polls/{id}/other") public String otherResponses(@PathVariable long id,
            @RequestParam(defaultValue="0") int page,Model model) {
        var rows=service.otherResponses(id,page); long count=service.otherResponseCount(id);
        if(page>0 && (long)page*10>=count) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        model.addAttribute("poll",service.poll(id,null)); model.addAttribute("responses",rows);
        model.addAttribute("page",page); model.addAttribute("total",count);
        return "admin/poll-other";
    }
    @PostMapping("/admin/community/polls/{id}/close") public String closePoll(@PathVariable long id,@RequestParam long version,RedirectAttributes flash) {
        service.closePoll(id,version); return done(flash);
    }
    @GetMapping("/admin/messages") public String messages(@RequestParam(defaultValue="PENDING") String status,@RequestParam(defaultValue="0") int page,Model model) {
        var rows=service.messages(status,page); long total=service.messageCount(status); int pages=(int)((total+9)/10);
        if(page>0 && page>=pages) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        model.addAttribute("messages",rows); model.addAttribute("filterStatus",status);
        model.addAttribute("page",page); model.addAttribute("pages",pages); model.addAttribute("total",total);
        return "admin/messages";
    }
    @PostMapping("/admin/messages/{id}/{action}") public String moderate(@PathVariable long id,@PathVariable String action,
            @RequestParam long version,@RequestParam(required=false) String reply,RedirectAttributes flash) {
        service.moderate(id,version,action,reply); flash.addFlashAttribute("notice","留言已处理。"); return "redirect:/admin/messages";
    }
    private String done(RedirectAttributes flash) { flash.addFlashAttribute("notice","已保存。"); return "redirect:/admin/overview"; }
}
