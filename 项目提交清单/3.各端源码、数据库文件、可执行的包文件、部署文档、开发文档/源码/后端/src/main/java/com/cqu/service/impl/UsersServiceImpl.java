package com.cqu.service.impl;

import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cqu.entity.Users;
import com.cqu.mapper.UsersMapper;
import com.cqu.security.RoleCodes;
import com.cqu.service.IUsersService;
import org.springframework.stereotype.Service;

/**
 * 用户表服务：注册默认 GROWER；禁止自选 SYS_ADMIN；登录返回归一化角色。
 */
@Service
public class UsersServiceImpl extends ServiceImpl<UsersMapper, Users> implements IUsersService {

    @Override
    public Users register(Users users) {
        LambdaQueryWrapper<Users> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Users::getUsername, users.getUsername());
        if (this.count(wrapper) > 0) {
            throw new RuntimeException("用户名已存在");
        }

        users.setPassword(BCrypt.hashpw(users.getPassword()));

        String role = users.getRole();
        if (role == null || role.isBlank()) {
            users.setRole(RoleCodes.GROWER);
        } else {
            String normalized = RoleCodes.normalize(role);
            if (!RoleCodes.isRegisterable(normalized)) {
                throw new RuntimeException("不可注册该角色");
            }
            users.setRole(normalized);
        }

        this.save(users);
        return users;
    }

    @Override
    public Users login(String username, String password) {
        LambdaQueryWrapper<Users> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Users::getUsername, username);
        Users users = this.getOne(wrapper);

        if (users == null) {
            throw new RuntimeException("用户名或密码错误");
        }

        if (!BCrypt.checkpw(password, users.getPassword())) {
            throw new RuntimeException("用户名或密码错误");
        }

        users.setRole(RoleCodes.normalize(users.getRole()));
        return users;
    }
}
