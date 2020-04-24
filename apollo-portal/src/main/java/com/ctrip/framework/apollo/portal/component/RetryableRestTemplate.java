package com.ctrip.framework.apollo.portal.component;

import java.net.SocketTimeoutException;
import java.util.List;

import javax.annotation.PostConstruct;

import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.HttpHostConnectException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.UriTemplateHandler;

import com.ctrip.framework.apollo.common.exception.ServiceException;
import com.ctrip.framework.apollo.core.dto.ServiceDTO;
import com.ctrip.framework.apollo.portal.constant.TracerEventType;
import com.ctrip.framework.apollo.portal.environment.Env;
import com.ctrip.framework.apollo.portal.environment.PortalMetaDomainService;
import com.ctrip.framework.apollo.tracer.Tracer;
import com.ctrip.framework.apollo.tracer.spi.Transaction;

/**
 * 封装RestTemplate. admin server集群在某些机器宕机或者超时的情况下轮询重试<br>
 * 将admin server列表乱序, 遍历调用接口, 有宕机的服务就会错误, 尝试下一个服务
 */
@Component
public class RetryableRestTemplate {

    private Logger logger = LoggerFactory.getLogger(RetryableRestTemplate.class);

    private UriTemplateHandler uriTemplateHandler = new DefaultUriBuilderFactory();

    private RestTemplate restTemplate;

    private final RestTemplateFactory restTemplateFactory;
    private final AdminServiceAddressLocator adminServiceAddressLocator;
    private final PortalMetaDomainService portalMetaDomainService;

    public RetryableRestTemplate(final @Lazy RestTemplateFactory restTemplateFactory,
            final @Lazy AdminServiceAddressLocator adminServiceAddressLocator,
            final PortalMetaDomainService portalMetaDomainService) {
        this.restTemplateFactory = restTemplateFactory;
        this.adminServiceAddressLocator = adminServiceAddressLocator;
        this.portalMetaDomainService = portalMetaDomainService;
    }

    /**
     * 依赖注入完成之后执行
     * 
     * void
     * 
     * @date: 2020年4月23日 上午9:58:15
     */
    @PostConstruct
    private void postConstruct() {
        restTemplate = restTemplateFactory.getObject();
    }

    public <T> T get(Env env, String path, Class<T> responseType, Object... urlVariables) throws RestClientException {
        return execute(HttpMethod.GET, env, path, null, responseType, urlVariables);
    }

    public <T> ResponseEntity<T> get(Env env, String path, ParameterizedTypeReference<T> reference,
            Object... uriVariables) throws RestClientException {

        return exchangeGet(env, path, reference, uriVariables);
    }

    public <T> T post(Env env, String path, Object request, Class<T> responseType, Object... uriVariables)
            throws RestClientException {
        return execute(HttpMethod.POST, env, path, request, responseType, uriVariables);
    }

    public void put(Env env, String path, Object request, Object... urlVariables) throws RestClientException {
        execute(HttpMethod.PUT, env, path, request, null, urlVariables);
    }

    public void delete(Env env, String path, Object... urlVariables) throws RestClientException {
        execute(HttpMethod.DELETE, env, path, null, null, urlVariables);
    }

    /**
     * 执行请求
     * 
     * @param method
     * @param env
     *            请求所属环境
     * @param path
     *            请求路径
     * @param request
     * @param responseType
     * @param uriVariables
     * @return T
     * @date: 2020年4月23日 上午10:08:44
     */
    private <T> T execute(HttpMethod method, Env env, String path, Object request, Class<T> responseType,
            Object... uriVariables) {

        if (path.startsWith("/")) {
            path = path.substring(1, path.length());
        }

        String uri = uriTemplateHandler.expand(path, uriVariables).getPath();
        Transaction ct = Tracer.newTransaction("AdminAPI", uri);
        ct.addData("Env", env);

        List<ServiceDTO> services = getAdminServices(env, ct);

        // 有一个调用成功, 就返回了
        for (ServiceDTO serviceDTO : services) {
            try {

                T result = doExecute(method, serviceDTO, path, request, responseType, uriVariables);

                ct.setStatus(Transaction.SUCCESS);
                ct.complete();
                return result;
            } catch (Throwable t) {
                logger.error("Http request failed, uri: {}, method: {}", uri, method, t);
                Tracer.logError(t);
                if (canRetry(t, method)) {
                    Tracer.logEvent(TracerEventType.API_RETRY, uri);
                } else {// biz exception rethrow
                    ct.setStatus(t);
                    ct.complete();
                    throw t;
                }
            }
        }

        // all admin server down
        ServiceException e = new ServiceException(
                String.format("Admin servers are unresponsive. meta server address: %s, admin servers: %s",
                        portalMetaDomainService.getDomain(env), services));
        ct.setStatus(e);
        ct.complete();
        throw e;
    }

    private <T> ResponseEntity<T> exchangeGet(Env env, String path, ParameterizedTypeReference<T> reference,
            Object... uriVariables) {
        if (path.startsWith("/")) {
            path = path.substring(1, path.length());
        }

        String uri = uriTemplateHandler.expand(path, uriVariables).getPath();
        Transaction ct = Tracer.newTransaction("AdminAPI", uri);
        ct.addData("Env", env);

        List<ServiceDTO> services = getAdminServices(env, ct);

        for (ServiceDTO serviceDTO : services) {
            try {

                ResponseEntity<T> result = restTemplate.exchange(parseHost(serviceDTO) + path, HttpMethod.GET, null,
                        reference, uriVariables);

                ct.setStatus(Transaction.SUCCESS);
                ct.complete();
                return result;
            } catch (Throwable t) {
                logger.error("Http request failed, uri: {}, method: {}", uri, HttpMethod.GET, t);
                Tracer.logError(t);
                if (canRetry(t, HttpMethod.GET)) {
                    Tracer.logEvent(TracerEventType.API_RETRY, uri);
                } else {// biz exception rethrow
                    ct.setStatus(t);
                    ct.complete();
                    throw t;
                }

            }
        }

        // all admin server down
        ServiceException e = new ServiceException(
                String.format("Admin servers are unresponsive. meta server address: %s, admin servers: %s",
                        portalMetaDomainService.getDomain(env), services));
        ct.setStatus(e);
        ct.complete();
        throw e;

    }

    /**
     * 获取env对应admin服务信息(每次调用列表顺序不同)
     * 
     * @param env
     * @param ct
     * @return List<ServiceDTO>
     * @date: 2020年4月23日 上午10:09:39
     */
    private List<ServiceDTO> getAdminServices(Env env, Transaction ct) {

        List<ServiceDTO> services = adminServiceAddressLocator.getServiceList(env);

        if (CollectionUtils.isEmpty(services)) {
            ServiceException e = new ServiceException(String.format("No available admin server."
                    + " Maybe because of meta server down or all admin server down. " + "Meta server address: %s",
                    portalMetaDomainService.getDomain(env)));
            ct.setStatus(e);
            ct.complete();
            throw e;
        }

        return services;
    }

    /**
     * 执行请求
     * 
     * @param method
     * @param service
     * @param path
     * @param request
     * @param responseType
     * @param uriVariables
     * @return T
     * @date: 2020年4月23日 上午10:57:35
     */
    private <T> T doExecute(HttpMethod method, ServiceDTO service, String path, Object request, Class<T> responseType,
            Object... uriVariables) {
        T result = null;
        switch (method) {
        case GET:
            result = restTemplate.getForObject(parseHost(service) + path, responseType, uriVariables);
            break;
        case POST:
            result = restTemplate.postForEntity(parseHost(service) + path, request, responseType, uriVariables)
                    .getBody();
            break;
        case PUT:
            restTemplate.put(parseHost(service) + path, request, uriVariables);
            break;
        case DELETE:
            restTemplate.delete(parseHost(service) + path, uriVariables);
            break;
        default:
            throw new UnsupportedOperationException(String.format("unsupported http method(method=%s)", method));
        }
        return result;
    }

    /**
     * 解析host
     * 
     * @param serviceAddress
     * @return String
     * @date: 2020年4月23日 上午10:57:54
     */
    private String parseHost(ServiceDTO serviceAddress) {
        return serviceAddress.getHomepageUrl() + "/";
    }

    // post,delete,put请求在admin server处理超时情况下不重试
    private boolean canRetry(Throwable e, HttpMethod method) {
        Throwable nestedException = e.getCause();
        if (method == HttpMethod.GET) {
            return nestedException instanceof SocketTimeoutException
                    || nestedException instanceof HttpHostConnectException
                    || nestedException instanceof ConnectTimeoutException;
        }
        return nestedException instanceof HttpHostConnectException
                || nestedException instanceof ConnectTimeoutException;
    }

}
