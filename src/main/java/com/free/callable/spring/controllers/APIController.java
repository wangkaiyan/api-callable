package com.free.callable.spring.controllers;

import com.free.callable.call.Callable;
import com.free.callable.exception.CallableException;
import com.free.callable.ip.IPUtil;
import com.free.callable.process.GlobalInstance;
import com.free.callable.process.pool.CallablePool;
import com.free.callable.process.pool.ExecutorPool;
import com.free.callable.process.pool.ProtocolPool;
import com.free.callable.process.print.AbstractProtocolPrint;
import com.free.callable.process.print.JsonProtocolPrint;
import com.free.callable.process.security.transport.TransportSecurity;
import com.free.callable.spring.util.ApplicationContextUtil;
import com.free.framework.common.api.struct.request.BaseRequest;
import com.free.framework.common.constants.HttpStatus;
import com.free.framework.common.exception.ServiceException;
import com.free.framework.common.log.APILogger;
import com.smart.validate.exception.SmartValidateException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Date;


@Controller
@RequestMapping("/api")
public class APIController {

    private CallablePool callablePool = GlobalInstance.getCallablePool();

    private ExecutorPool executorPool = GlobalInstance.getExecutorPool();

    private ProtocolPool protocolPool = GlobalInstance.getProtocolPool();


    @RequestMapping(value="{protocolAlias}/{serviceAlias}/{methodAlias}")
    public void handler(HttpServletRequest  request, HttpServletResponse response,
                        @PathVariable(value="protocolAlias") String protocolAlias,
                        @PathVariable(value="serviceAlias") String serviceAlias,
                        @PathVariable(value="methodAlias") String methodAlias) throws IOException {

        long startTime = System.currentTimeMillis();
        int code = 200;

        TransportSecurity transportSecurity = GlobalInstance.getTransportSecurity();
        String responseAsString = null;
        AbstractProtocolPrint print = ApplicationContextUtil.getBeansOfType(JsonProtocolPrint.class);

        try {

            APILogger.getCurrRecord().setRequestFileds(new Date(), request.getRequestURI(), methodAlias, null, null, IPUtil.getIpAddress(request));

            transportSecurity.request(request);

            //print = protocolPool.getProtocolPrint(protocolAlias);

            BaseRequest baseRequest = callablePool.getBaseRequest(print, request);

            Callable callable = callablePool.getCallable(serviceAlias, baseRequest);

            Method executorMethod = executorPool.getExecutor(callable.getClass(), methodAlias);

            responseAsString = callable.call(print, executorMethod, request, response);

        }

        catch(ServiceException e) {
            code = e.getReturnInfo().getCode();
            responseAsString = print.error(code, e.getReturnInfo().getMessage());
        }
        catch(CallableException e) {
            code = HttpStatus.BAD_REQUEST.getStatusCode();
            responseAsString = print.error(code, e.getMessage());
        }
        catch(SmartValidateException e) {
            code = HttpStatus.BAD_REQUEST.getStatusCode();
            responseAsString = print.error(code, e.getMessage());
        }
        catch(InvocationTargetException e) {
            if(e.getTargetException() instanceof ServiceException) {
                ServiceException s = (ServiceException) e.getTargetException();
                code = s.getReturnInfo().getCode();
                responseAsString = print.error(code, s.getReturnInfo().getMessage());
            }
            else {
                e.printStackTrace();
                code = HttpStatus.INTERNAL_SERVER_ERROR.getStatusCode();
                responseAsString = print.error(code, HttpStatus.INTERNAL_SERVER_ERROR.toString());
            }
        }
        catch (Exception e) {
            e.printStackTrace();
            code = HttpStatus.INTERNAL_SERVER_ERROR.getStatusCode();
            responseAsString = print.error(code, HttpStatus.INTERNAL_SERVER_ERROR.toString());
        }

        try {
            responseAsString = transportSecurity.response(request, response, responseAsString);
        } catch (CallableException e) {
            e.printStackTrace();
            code = HttpStatus.BAD_REQUEST.getStatusCode();
            responseAsString = print.error(code, HttpStatus.BAD_REQUEST.toString());
        }

        response.setCharacterEncoding("utf-8");
        response.setContentType(print.getContentType());
        response.getWriter().print(responseAsString);

        long endTime = System.currentTimeMillis();

        APILogger.getCurrRecord().setResponseFileds(code, endTime - startTime);
        try {
            APILogger.doLog();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
