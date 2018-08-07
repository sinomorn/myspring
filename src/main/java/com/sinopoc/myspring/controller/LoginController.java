package com.sinopoc.myspring.controller;

import com.sinopoc.annotation.KAutowired;
import com.sinopoc.annotation.KController;
import com.sinopoc.annotation.KRequestMapping;
import com.sinopoc.myspring.service.LoginService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@KController()
@KRequestMapping("/login")
public class LoginController {

    @KAutowired
    private LoginService loginService;

    @KRequestMapping("/query")
    public  void query(String name, HttpServletResponse res) throws IOException {
        System.out.println("query user controller  is invoked" );
//        String s = loginService.queryUserById("1");
        res.getWriter().println("Welcome! "+name);
//        return name;
    }
}
