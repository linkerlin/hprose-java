/**********************************************************\
|                                                          |
|                          hprose                          |
|                                                          |
| Official WebSite: http://www.hprose.com/                 |
|                   http://www.hprose.net/                 |
|                   http://www.hprose.org/                 |
|                                                          |
\**********************************************************/
/**********************************************************\
 *                                                        *
 * HproseHttpService.java                                 *
 *                                                        *
 * hprose http service class for Java.                    *
 *                                                        *
 * LastModified: Apr 17, 2014                             *
 * Author: Ma Bingyao <andot@hprose.com>                  *
 *                                                        *
\**********************************************************/
package hprose.server;

import hprose.common.HproseMethods;
import hprose.io.ByteBufferStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

public class HproseHttpService extends HproseService {
    private boolean crossDomainEnabled = false;
    private boolean p3pEnabled = false;
    private boolean getEnabled = true;
    private final HashMap<String, Boolean> origins = new HashMap<String, Boolean>();
    private static final ThreadLocal<HttpContext> currentContext = new ThreadLocal<HttpContext>();

    public static HttpContext getCurrentContext() {
        return currentContext.get();
    }

    @Override
    public HproseMethods getGlobalMethods() {
        if (globalMethods == null) {
            globalMethods = new HproseHttpMethods();
        }
        return globalMethods;
    }

    @Override
    public void setGlobalMethods(HproseMethods methods) {
        if (methods instanceof HproseHttpMethods) {
            this.globalMethods = methods;
        }
        else {
            throw new ClassCastException("methods must be a HproseHttpMethods instance");
        }
    }

    public boolean isCrossDomainEnabled() {
        return crossDomainEnabled;
    }

    public void setCrossDomainEnabled(boolean enabled) {
        crossDomainEnabled = enabled;
    }

    public boolean isP3pEnabled() {
        return p3pEnabled;
    }

    public void setP3pEnabled(boolean enabled) {
        p3pEnabled = enabled;
    }

    public boolean isGetEnabled() {
        return getEnabled;
    }

    public void setGetEnabled(boolean enabled) {
        getEnabled = enabled;
    }

    public void addAccessControlAllowOrigin(String origin) {
        origins.put(origin, true);
    }

    public void removeAccessControlAllowOrigin(String origin) {
        origins.remove(origin);
    }

    @Override
    protected Object[] fixArguments(Type[] argumentTypes, Object[] arguments, int count, Object context) {
        HttpContext httpContext = (HttpContext)context;
        if (argumentTypes.length != count) {
            Object[] args = new Object[argumentTypes.length];
            System.arraycopy(arguments, 0, args, 0, count);
            Class<?> argType = (Class<?>) argumentTypes[count];
            if (argType.equals(HttpContext.class)) {
                args[count] = httpContext;
            }
            else if (argType.equals(HttpServletRequest.class)) {
                args[count] = httpContext.getRequest();
            }
            else if (argType.equals(HttpServletResponse.class)) {
                args[count] = httpContext.getResponse();
            }
            else if (argType.equals(HttpSession.class)) {
                args[count] = httpContext.getSession();
            }
            else if (argType.equals(ServletContext.class)) {
                args[count] = httpContext.getApplication();
            }
            else if (argType.equals(ServletConfig.class)) {
                args[count] = httpContext.getConfig();
            }
            return args;
        }
        return arguments;
    }

    protected void sendHeader(HttpContext httpContext) throws IOException {
        if (event != null && HproseHttpServiceEvent.class.isInstance(event)) {
            ((HproseHttpServiceEvent)event).onSendHeader(httpContext);
        }
        HttpServletRequest request = httpContext.getRequest();
        HttpServletResponse response = httpContext.getResponse();
        response.setContentType("text/plain");
        if (p3pEnabled) {
            response.setHeader("P3P", "CP=\"CAO DSP COR CUR ADM DEV TAI PSA PSD " +
                                      "IVAi IVDi CONi TELo OTPi OUR DELi SAMi " +
                                      "OTRi UNRi PUBi IND PHY ONL UNI PUR FIN " +
                                      "COM NAV INT DEM CNT STA POL HEA PRE GOV\"");
        }
        if (crossDomainEnabled) {
            String origin = request.getHeader("Origin");
            if (origin != null && !origin.equals("null")) {
                if (origins.isEmpty() || origins.containsKey(origin)) {
                    response.setHeader("Access-Control-Allow-Origin", origin);
                    response.setHeader("Access-Control-Allow-Credentials", "true");
                }
            }
            else {
                response.setHeader("Access-Control-Allow-Origin", "*");
            }
        }
    }

    public void handle(HttpContext httpContext) throws IOException {
        handle(httpContext, null);
    }

    public void handle(HttpContext httpContext, HproseHttpMethods methods) throws IOException {
        ByteBufferStream ostream = null;
        try {
            currentContext.set(httpContext);
            sendHeader(httpContext);
            String method = httpContext.getRequest().getMethod();
            if (method.equals("GET") && getEnabled) {
                ostream = doFunctionList(methods, httpContext);
                ostream.writeTo(httpContext.getResponse().getOutputStream());
            }
            else if (method.equals("POST")) {
                ByteBufferStream istream = new ByteBufferStream();
                istream.readFrom(httpContext.getRequest().getInputStream());
                ostream = handle(istream, methods, httpContext);
                istream.close();
                ostream.writeTo(httpContext.getResponse().getOutputStream());
            }
            else {
                httpContext.getResponse().sendError(HttpServletResponse.SC_FORBIDDEN);
            }
        }
        finally {
            currentContext.remove();
            if (ostream != null) {
                ostream.close();
            }
        }
    }
}