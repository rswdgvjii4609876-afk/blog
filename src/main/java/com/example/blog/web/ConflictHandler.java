package com.example.blog.web;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

@ControllerAdvice
public class ConflictHandler {
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ModelAndView concurrentWrite() {
        ModelAndView view = new ModelAndView("error");
        view.setStatus(HttpStatus.CONFLICT);
        view.addObject("status", 409);
        return view;
    }
}
