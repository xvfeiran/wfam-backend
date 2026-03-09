package com.bosch.rbcc.aftermarketpartsmanagementsystem.interceptor;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.header.CommonHeaderManager;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.header.CommonHeaders;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class CommonHeadersInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        // CORS 预检请求不携带认证头，直接放行
        if (HttpMethod.OPTIONS.name().equals(request.getMethod())) {
            return true;
        }
        String token = request.getHeader("x-authentication-header");
        ObjectMapper mapper = new ObjectMapper();
        CommonHeaders commonHeaders = mapper.readValue(token, CommonHeaders.class);
        CommonHeaderManager.setCommonHeaders(commonHeaders);
        return true;
    }

    @Override
    public void postHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                           @NonNull Object handler, ModelAndView modelAndView) {
        // 在 controller 方法处理后，视图渲染前执行
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                   @NonNull Object handler, Exception ex) {
        // 在请求处理完成后执行，即视图渲染完成后执行
        // 清理 CommonHeaderManager 中的内容
        CommonHeaderManager.removeCommonHeaders();
    }
}
