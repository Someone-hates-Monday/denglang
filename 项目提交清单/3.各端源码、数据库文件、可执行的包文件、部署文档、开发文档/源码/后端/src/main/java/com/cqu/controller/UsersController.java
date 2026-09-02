package com.cqu.controller;

import com.cqu.entity.Users;
import com.cqu.service.IUsersService;
import com.cqu.utils.JwtProperties;
import com.cqu.vo.Result;
import com.cqu.vo.LoginVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/users")
public class UsersController {

    @Autowired
    private IUsersService usersService;

    @Autowired
    private JwtProperties jwtProperties;

    /**
     * 注册
     */
    @PostMapping("/register")
    public Result<String> register(@RequestBody Users users) {
        log.info("用户注册: {}", users.getUsername());
        usersService.register(users);
        return Result.success("注册成功");
    }

    /**
     * 登录
     */
    @PostMapping("/login")
    public Result<LoginVO> login(@RequestBody Users users) {
        log.info("用户登录: {}", users.getUsername());
        Users loginUser = usersService.login(users.getUsername(), users.getPassword());

        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", loginUser.getId());
        claims.put("role", loginUser.getRole());
        String token = jwtProperties.createAccessToken(claims);

        LoginVO loginVO = LoginVO.builder()
                .token(token)
                .userId(String.valueOf(loginUser.getId()))
                .username(loginUser.getUsername())
                .role(loginUser.getRole())
                .build();

        log.info("用户 {} 登录成功", users.getUsername());
        return Result.success(loginVO);
    }
}
