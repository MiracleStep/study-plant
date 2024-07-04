package com.tianji.authsdk.resource.interceptors;

import com.tianji.auth.common.constants.JwtConstants;
import com.tianji.common.utils.UserContext;
import feign.RequestInterceptor;
import feign.RequestTemplate;

/**
 * Feign拦截器，发起远程调用时，用于设置Feign请求头中的userId传递到被调用方
 */
public class FeignRelayUserInterceptor implements RequestInterceptor {
    @Override
    public void apply(RequestTemplate template) {
        Long userId = UserContext.getUser();
        if (userId == null) {
            return;
        }
        template.header(JwtConstants.USER_HEADER, userId.toString());
    }
}
