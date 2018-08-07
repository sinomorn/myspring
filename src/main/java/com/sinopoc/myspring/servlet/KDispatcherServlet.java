package com.sinopoc.myspring.servlet;

import com.sinopoc.annotation.KAutowired;
import com.sinopoc.annotation.KController;
import com.sinopoc.annotation.KRequestMapping;
import com.sinopoc.annotation.KService;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;

public class KDispatcherServlet extends HttpServlet{

    private Properties p=new Properties();

    private List<String> classNames=new ArrayList<String>();


    private Map<String,Object> Ioc=new HashMap<String,Object>();

    private Map<String,Method> handlerMapping=new HashMap<String,Method>();

    private Map<String,Object> controllerMap=new HashMap<>();
    //初始化阶段调用的 方法
    @Override
    public void init(ServletConfig config) throws ServletException {
        //1.加载配置文件
        String contextConfigLocation = config.getInitParameter("contextConfigLocation");
        doLoad(contextConfigLocation);

        //2.扫描所有配置类
        doScanner(p.getProperty("scannerPackage"));

        //3.实例化所有配置类,并放入IOC容器中,map<String,Object>
        doInstance();


        //4.实现依赖注入
        doAutowired();

        //5.初始化HandlerMapping
        try {
            initHandlerMapping();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        }

        super.init(config);
    }

    private void initHandlerMapping() throws IllegalAccessException, InstantiationException {
        //1.判断IOC是否为空
        if(Ioc.isEmpty()){return;}
        //2.遍历Ioc容器,探讨每一个带controller注解的类
        for (Map.Entry<String,Object> realClass:Ioc.entrySet() ) {
            //如果不带controller注解的,继续
            Class<?> clazz = realClass.getValue().getClass();
            if(!clazz.isAnnotationPresent(KController.class)){
                continue;
            }
            //反射获取类上的requestmapping
            String baseUrl="";
            if(clazz.isAnnotationPresent(KRequestMapping.class)){
                baseUrl = clazz.getAnnotation(KRequestMapping.class).value().replaceAll("/+", "/");
            }
            //获取所有方法方法,和方法上的 requestmapping  路径名:方法对象,存到map中
            Method[] methods = clazz.getMethods();

            for (Method method:methods) {
                //判断是否有requestmapping
                if(!method.isAnnotationPresent(KRequestMapping.class)){continue;}

                String methodUrl = method.getAnnotation(KRequestMapping.class).value().replaceAll("/+", "/");

                //baseurl+methodurl  作为k,method作为v 赋值给Handlermapping
                String url=baseUrl+methodUrl;

                handlerMapping.put(url,method);
                controllerMap.put(url,clazz.newInstance());

            }
        }
    }

    private void doAutowired() {
        //1.ioc是否为空,返回
        if(Ioc.isEmpty()){return;}

        //遍历Ioc容器,找出每个类中的每个field

        for (Map.Entry<String,Object> entry:Ioc.entrySet()) {
            //获取到每个类对象
            Object object = entry.getValue();

            Field[] fields = object.getClass().getDeclaredFields();

            for (Field field:fields) {
                System.out.println(field.getName());
                //找出所有有注解的成员变量
                if(!field.isAnnotationPresent(KAutowired.class)){ continue;}

                //过滤出autowired下的,获取成员变量的value,判断是否指明注入方,否则就是接口注入
                String objectName = field.getAnnotation(KAutowired.class).value().trim();
                if ("".equals(objectName)){
                    //意味着是接口注入
                    objectName=field.getType().getName();

                }

                //前期都是为了根据autowired确定k值
                //接下来根据k从ioc中匹配,给field赋值
                //首先暴力反射
                field.setAccessible(true);

                //赋值
                try {
                    field.set(object,Ioc.get(objectName));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }

            }

        }


    }

    private void doInstance() {
        //判断集合是否为空,是则返回
        if(classNames.isEmpty()){return;}

        //遍历list集合,通过反射进行实例化,并放到map中
        try {
            for (String className:classNames) {

                Class<?> realClass = Class.forName(className);
                String objectName =null;
                //只有有注解的才实例化
                if(realClass.isAnnotationPresent(KController.class)){
                    //controller使用默认的开头小写
                    objectName=lowerFirstCase(realClass.getSimpleName());

                    Ioc.put(objectName,realClass.newInstance());

                }else if(realClass.isAnnotationPresent(KService.class)){
                    //2.如果指定名,用指定的名字   获得注解对象的value
                    KService kService = realClass.getAnnotation(KService.class);
                    objectName = kService.value();
                    if("".equals(objectName.trim())){
                        //如果没有指定
                        objectName =lowerFirstCase(realClass.getSimpleName());
                    }

                    Ioc.put(objectName,realClass.newInstance());

                    //3.如果是接口,用接口的类型作为k
                    Class<?>[] interfaces = realClass.getInterfaces();
                    for (Class i:interfaces) {
                           Ioc.put(i.getName(),realClass.newInstance()) ;

                    }
                }else{
                    continue;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }


    }

    private String lowerFirstCase(String simpleName) {
        char[] chars = simpleName.toCharArray();
        chars[0]+=32;
        return String.valueOf(chars);
    }

    private void doScanner(String scannerPackage) {
    //进行递归扫描
        URL url = this.getClass().getClassLoader().getResource("/" + scannerPackage.replaceAll("\\.", "/"));
        File classDir = new File(url.getFile());
        for (File file:classDir.listFiles()) {
            if(file.isDirectory()){
                //scannerPackage:  com.keviv
                doScanner(scannerPackage+"."+file.getName());
            }else{
                //记录所有类的全路径名,且是不带.class的
                classNames.add(scannerPackage+"."+file.getName().replaceAll("\\.class",""));

            }
        }

    }

    private void doLoad(String contextConfigLocation) {
        //用classloader获取该文件的stream
        InputStream resourceAsStream = this.getClass().getClassLoader().getResourceAsStream(contextConfigLocation);

        try {
            p.load(resourceAsStream);
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            if(null!=resourceAsStream){
                try {
                    resourceAsStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doPost(req, resp);
    }

    //运行时阶段执行的方法
    //6.等待请求
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
       try {
           doDispatch(req,resp);
       }catch(Exception e){
           resp.getWriter().write("500 ! the server is crackdown");

       }
    }

    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            //根据uri匹配handlerMapping中对应的方法,并调用
        // /myspring/query
        String requestURI = req.getRequestURI();
        ///myspring
        String contextPath = req.getContextPath();
        String realUri = requestURI.replace(contextPath, "").replaceAll("/+", "/");

        //判断handlermapping中是否包含
        if(!handlerMapping.containsKey(realUri)){
            resp.getWriter().write("404! not found!");
            return ;
        }

        //获取方法的参数列表
        Method method = handlerMapping.get(realUri);

        Class<?>[] parameterTypes = method.getParameterTypes();

        Map<String,String[]> parameterMap = req.getParameterMap();

        Object[] patamsValues = new Object[parameterTypes.length];

        for (int i = 0; i <parameterTypes.length ; i++) {

            String simpleName = parameterTypes[i].getSimpleName();

            if("HttpServletRequest".equals(simpleName)){
                patamsValues[i]=req;
                continue;
            }
            if("HttpServletResponse".equals(simpleName)){
                patamsValues[i]=resp;
                continue;
            }
            if("String".equals(simpleName)){
               //如果是一般参数,就遍历所有request携带的参数,分别赋值给object[]
                for (Map.Entry<String,String[]> param:parameterMap.entrySet()){
                    patamsValues[i]=Arrays.toString( param.getValue());
                }
                continue;
            }

        }

        //最后调用方法
        try {
            Object o = this.controllerMap.get(realUri);
            System.out.println(o);
            method.invoke(o, patamsValues);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }

    }


}
