package com.caishi.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.format.support.FormattingConversionService;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;


/**
 * @author 11723
 */
@Slf4j
@ConfigurationProperties(prefix = "api.json")
@Configuration(value = "rootSpringMvcConfig")
public class SpringMvcConfig implements WebMvcConfigurer, InitializingBean {

    @Resource(name = "requestMappingHandlerAdapter")
    private RequestMappingHandlerAdapter adapter;

    @Resource
    private ApplicationContext applicationContext;

    @Resource(name = "mvcConversionService")
    private FormattingConversionService formattingConversionService;

    /**
     * 默认日期时间格式
     */
    public static final String DEFAULT_DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";
    /**
     * 默认日期格式
     */
    public static final String DEFAULT_DATE_FORMAT = "yyyy-MM-dd";
    /**
     * 默认时间格式
     */
    public static final String DEFAULT_TIME_FORMAT = "HH:mm:ss";

    @Getter
    @Setter
    private Boolean jsonFormat = false;

    private ApiJsonParamResolver apiJsonParamResolver;


    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(apiJsonParamResolver());
    }


    /**
     * <p>
     * <h3>作者 keray</h3>
     * <h3>时间： 2019/9/3 14:50</h3>
     * 添加自定义ApiJsonParam装载器
     * </p>
     *
     * @param
     * @return <p> {@link } </p>
     * @throws
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        List<HandlerMethodArgumentResolver> resolvers = new LinkedList<>();
        resolvers.add(apiJsonParamResolver());
        if (adapter.getArgumentResolvers() == null) {
            log.warn("adapter 没有原生的装载器");
        } else {
            resolvers.addAll(adapter.getArgumentResolvers());
        }
        adapter.setArgumentResolvers(resolvers);
    }


    private ApiJsonParamResolver apiJsonParamResolver() {
        objectMapper();
        if (apiJsonParamResolver != null) {
            return apiJsonParamResolver;
        }
        apiJsonParamResolver = new ApiJsonParamResolver(adapter.getMessageConverters(), adapter.getArgumentResolvers(), jsonFormat);
        return apiJsonParamResolver;
    }

    /**
     * <p>
     * <h3>作者 keray</h3>
     * <h3>时间： 2019/9/3 14:50</h3>
     * 拿到支持可以支持localDateTime的converter列表
     * </p>
     *
     * @param
     * @return <p> {@link List <HttpMessageConverter<?>>} </p>
     * @throws
     */
    private void objectMapper() {
        HttpMessageConverter co = null;
        for (HttpMessageConverter c : adapter.getMessageConverters()) {
            if (c instanceof MappingJackson2HttpMessageConverter) {
                co = c;
            }
        }

        // 构建支持LocalDatetime的ObjectMapper
        Map<String, Module> map = BeanFactoryUtils.beansOfTypeIncludingAncestors(applicationContext, Module.class);
        map.put("javaModule", javaModule());
        Jackson2ObjectMapperBuilder jackson2ObjectMapperBuilder = new Jackson2ObjectMapperBuilder().modules(
                map.values().toArray(new Module[0])
        );
        ObjectMapper objectMapper = jackson2ObjectMapperBuilder.build();
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);
        // 移除原有的MappingJackson2HttpMessageConverter
        // （原有：项目原先混乱的结构导致直接注入的支持LocalDatetime的JavaTimeModule被不支持的覆盖掉，这里就直接替换了MappingJackson2HttpMessageConverter）
        adapter.getMessageConverters().remove(co);
        // 新增支持localDatetime的MappingJackson2HttpMessageConverter
        adapter.getMessageConverters().add(new MappingJackson2HttpMessageConverter(objectMapper));
    }

    /**
     * <p>
     * <h3>作者 keray</h3>
     * <h3>时间： 2019/9/3 14:49</h3>
     * 注入localDateTime系列支持model
     * </p>
     *
     * @param
     * @return <p> {@link JavaTimeModule} </p>
     * @throws
     */
    private JavaTimeModule javaModule() {
        JavaTimeModule javaTimeModule = new JavaTimeModule();
        javaTimeModule.addSerializer(LocalDateTime.class, new com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer(DateTimeFormatter.ofPattern(DEFAULT_DATE_TIME_FORMAT)));
        javaTimeModule.addSerializer(LocalDate.class, new com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer(DateTimeFormatter.ofPattern(DEFAULT_DATE_FORMAT)));
        javaTimeModule.addSerializer(LocalTime.class, new com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer(DateTimeFormatter.ofPattern(DEFAULT_TIME_FORMAT)));
        javaTimeModule.addDeserializer(LocalDateTime.class, new com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer(DateTimeFormatter.ofPattern(DEFAULT_DATE_TIME_FORMAT)));
        javaTimeModule.addDeserializer(LocalDate.class, new com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer(DateTimeFormatter.ofPattern(DEFAULT_DATE_FORMAT)));
        javaTimeModule.addDeserializer(LocalTime.class, new com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer(DateTimeFormatter.ofPattern(DEFAULT_TIME_FORMAT)));
        return javaTimeModule;
    }

    /**
     * <p>
     * <h3>作者 keray</h3>
     * <h3>时间： 2019/9/3 14:53</h3>
     * 添加接口方法参数里的LocalDatetime支持 参数必须使用@RequestParam注解
     * </p>
     */
    @Bean
    public void localDateConverter() {
        formattingConversionService.addConverter(new Converter<String, LocalDate>() {
            @Override
            public LocalDate convert(String s) {
                return LocalDate.parse(s, DateTimeFormatter.ofPattern(DEFAULT_DATE_FORMAT));
            }
        });
        formattingConversionService.addConverter(new Converter<String, LocalDateTime>() {
            @Override
            public LocalDateTime convert(String s) {
                return LocalDateTime.parse(s, DateTimeFormatter.ofPattern(DEFAULT_DATE_TIME_FORMAT));
            }
        });
        formattingConversionService.addConverter(new Converter<String, LocalTime>() {
            @Override
            public LocalTime convert(String s) {
                return LocalTime.parse(s, DateTimeFormatter.ofPattern(DEFAULT_TIME_FORMAT));
            }
        });
    }
}
