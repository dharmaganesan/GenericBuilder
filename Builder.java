package com.imdganesan;

import static org.springframework.util.ReflectionUtils.*;
import static org.springframework.beans.BeanUtils.instantiate;

import org.springframework.cglib.proxy.Enhancer;
import org.springframework.cglib.proxy.MethodInterceptor;
import org.springframework.cglib.proxy.MethodProxy;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Created by Ganesan on 25/05/16.
 */
public class Builder<T> {


    private static final FieldFilter NON_STATIC_FILTER_FILTER = f-> !Modifier.isStatic(f.getModifiers());

    private Class<T> clazz;
    private T proxyInstance;
    private Map<String, Optional> properties;
    private MethodInterceptorImpl methodInterceptor;

    private Builder(Class<T> clazz, Function<Field, Object>  callBack) {
        this.clazz = clazz;
        this.properties = new HashMap<>();
        doWithFields(clazz, field ->  properties.put(field.getName(), Optional.ofNullable(callBack.apply(field))) , NON_STATIC_FILTER_FILTER);
        this.methodInterceptor = new MethodInterceptorImpl();
        this.proxyInstance = getProxyInstance(clazz, methodInterceptor);
    }
    
    
    public static <T> Builder<T> of(Class<T> clazz) {
        return new Builder<>(clazz, field -> null);
    }
    
    public static <T> Builder<T> of(Class<T> clazz, T instance) {
    	return new Builder<>(clazz, field -> {  field.setAccessible(true); return getField(field, instance); });
    }
    
    public <V> PropertyAccessor<T, V> on(Function<T, V> getter) {
        getter.apply(proxyInstance);
        String property = methodInterceptor.getLastAccessedProperty();
        return new PropertyAccessor<>(this, property);
    }


    public static final class PropertyAccessor<T, V> {

        private Builder<T> builder;
        private String propertyName;

        private PropertyAccessor(Builder<T> builder, String propertyName) {
            this.builder = builder;
            this.propertyName = propertyName;
        }

        public Optional<V> get() {
            return builder.properties.get(propertyName);
        }

        public Builder<T> set(V val) {
            builder.properties.put(propertyName, Optional.ofNullable(val));
            return builder;
        }

    }


    public T build() {
    	T instance = instantiate(clazz);
		doWithFields(clazz, field -> properties.computeIfPresent(field.getName(), 
				(key, val) -> {
			        makeAccessible(field);
			        setField(field, instance, val.orElse(null));
			        return val;
				}), 
				NON_STATIC_FILTER_FILTER);
		return instance;
    }

    private static class MethodInterceptorImpl implements MethodInterceptor {

        private String lastAccessedProperty;

        public Object intercept(Object obj, Method method, Object[] args,
                                MethodProxy proxy) throws Throwable {
            Optional<String> methodName = Optional.of(method.getName());
            lastAccessedProperty = Optional.ofNullable(methodName.filter(m -> m.startsWith("get"))
                    .filter(m -> m.length() > 3)
                    .map(m -> m.substring(3))
                    .orElse(
                            methodName
                                    .filter(m -> m.startsWith("is"))
                                    .filter(m -> m.length() > 2)
                                    .map(m -> m.substring(2))
                                    .orElse(null)

                    )).map(m -> m.substring(0, 1).toLowerCase() + m.substring(1))
                    .orElse(lastAccessedProperty);
            return null;
        }

        public String getLastAccessedProperty() {
            return lastAccessedProperty;
        }
    }

    private static  <T> T getProxyInstance(Class<T> clazz, MethodInterceptor methodInterceptor) {
        Enhancer enhancer = new Enhancer();
        enhancer.setCallback(methodInterceptor);
        enhancer.setSuperclass(clazz);
        return (T) enhancer.create();
    }

}
