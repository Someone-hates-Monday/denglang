package com.cqu.config;

import com.cqu.entity.Users;
import com.cqu.mapper.UsersMapper;
import com.cqu.security.RoleCapabilities;
import com.cqu.security.RoleCodes;
import com.cqu.security.RequireCap;
import com.cqu.utils.JwtProperties;
import com.cqu.utils.UserHolder;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
public class HttpAuthInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private UsersMapper usersMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        String token = request.getHeader("token");
        try {
            Claims claims = jwtProperties.parseJWT(token);
            Object userIdValue = claims.get("userId");
            if (!(userIdValue instanceof Number userIdNumber)) {
                response.setStatus(401);
                return false;
            }
            Long userId = userIdNumber.longValue();
            String role = null;
            Object roleClaim = claims.get("role");
            if (roleClaim != null) {
                role = RoleCodes.normalize(String.valueOf(roleClaim));
            }
            if (role == null || role.isBlank()) {
                Users u = usersMapper.selectById(userId);
                role = u != null ? RoleCodes.normalize(u.getRole()) : RoleCodes.GROWER;
            }
            UserHolder.set(userId, role);
            log.info("用户访问 id={} role={}", userId, role);

            RequireCap requireCap = handlerMethod.getMethodAnnotation(RequireCap.class);
            if (requireCap != null && requireCap.value().length > 0) {
                boolean ok = false;
                for (String cap : requireCap.value()) {
                    if (RoleCapabilities.can(role, cap)) {
                        ok = true;
                        break;
                    }
                }
                if (!ok) {
                    response.setStatus(403);
                    response.setCharacterEncoding("UTF-8");
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"code\":403,\"errorMsg\":\"无权限\",\"data\":null}");
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            response.setStatus(401);
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserHolder.remove();
    }
}
