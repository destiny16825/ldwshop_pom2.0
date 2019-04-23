package com.qf.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.fastjson.JSON;
import com.qf.entity.Email;
import com.qf.entity.User;
import com.qf.service.IUserService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Controller
@RequestMapping("/sso")
public class SsoController {

    @Reference
    private IUserService userService;

    @Autowired//缓存服务器
    private RedisTemplate redisTemplate;

    @Autowired//消息中间件
    private RabbitTemplate rabbitTemplate;

    /**
     * 跳转注册的方法
     * @return
     */
    @RequestMapping("/toRegister")
    public String toRegister(){
        return "register";
    }

    /**
     * 跳转登录的方法
     * @return
     */
    @RequestMapping("/toLogin")
    public String toLogin(String returnUrl, ModelMap map){
        map.put("returnUrl",returnUrl);
        return "login";
    }

    /**
     * 注册的方法
     * @return
     */
    @RequestMapping("/register")
    public String register(User user, ModelMap map){
        int result= userService.insertUser(user);
        if (result<=0){
            //注册失败
            map.put("error", "0");
            return "register";
        }

        //注册成功
        Email email=new Email();
        System.out.println("发送给谁："+user.getEmail());
        email.setTo(user.getEmail());;//设置发送给谁
        email.setSubject("NBA官方商城激活邮件");


        //生成uuid,并设置到redis服务器
        String uuid=UUID.randomUUID().toString();
        redisTemplate.opsForValue().set("email_token"+user.getUsername(),uuid);
        redisTemplate.expire("email_token"+user.getUsername(),5,TimeUnit.MINUTES);

        String url="http://localhost:8084/sso/activateUser?username="+user.getUsername()+"&token="+uuid;
        email.setContent("NBA官方商城账号激活链接地址：<a href='"+ url +"'>"+ url +"<a/>");
        email.setCreatetime(new Date());
        rabbitTemplate.convertAndSend("email_queue",email);

        return "login";
    }

    /**
     * 激活用户
     * @return
     */
    @RequestMapping("/activateUser")
    public String activateUser(String username,String token){
        //验证redis中的email_token是否正确
        String redisToken= (String) redisTemplate.opsForValue().get("email_token"+username);
        if (redisToken==null||!redisToken.equals(token)){
            //认证失败
            return "fail to activate" ;
        }
        //认证成功,激活用户
        userService.activateUser(username);
        //激活后跳转到登录页面
        return "redirect:/sso/toLogin";
    }

    /**
     * 登录的页面
     * @return
     */
    @RequestMapping("/login")
    public String login(String username, String password, ModelMap map, HttpServletResponse response,String returnUrl){
        User user = userService.loginUser(username, password);
        //登录失败，返回到登录页面
        if (user==null){
            System.out.println("登录失败");
            map.put("erro","0");
            return "login";
        }else if(user.getStatus() == 0){
            //未激活
            map.put("error", "1");

            String mail = user.getEmail();
            int index = mail.indexOf("@");
            System.out.println("邮箱后缀"+mail.substring(index + 1));
            String tomail = "http://mail." + mail.substring(index + 1);

           map.put("tomail", tomail);
            return "login";
        }

        //如果没有设置登录成功的url，则默认跳转回首页
        System.out.println(returnUrl);
        if(returnUrl == null || returnUrl.equals("")){
            returnUrl = "http://localhost:8081/";
        }

        //把用户信息存放到redis服务器中,设置缓存的时间
        String token= UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(token,user);
        redisTemplate.expire(token,10, TimeUnit.DAYS);

        //将用户令牌设置到cookie中
        Cookie cookie=new Cookie("login_token",token);
        cookie.setMaxAge(60*60*24*10);//时长
        cookie.setPath("/");
        response.addCookie(cookie);

        //登录成功
        return "redirect:"+returnUrl;
    }

    /**
     * 判断是登录状态
     * @param loginToken
     * @return
     */
    @RequestMapping("/isLogin")
    @ResponseBody
    public String isLogin(@CookieValue(name = "login_token",required = false) String loginToken){
        //获取浏览器cookie中的login_token
        System.out.println("cookie中的登录令牌："+loginToken);
        //从redis缓存中取得user对象
        User user=null;
        if (loginToken!=null){
           user=(User) redisTemplate.opsForValue().get(loginToken);
        }
        //将对象转化为json字符串返回

        return  user==null?"ifLogin(null)":"ifLogin(' "+ JSON.toJSONString(user) +" ')";
    }

    /**
     * 注销
     * @return
     */
    @RequestMapping("/logout")
    public String logout(@CookieValue(name = "login_token", required = false) String loginToken, HttpServletResponse response){

        //清空redis
        redisTemplate.delete(loginToken);

        //请求cookie
        Cookie cookie = new Cookie("login_token", null);
        cookie.setMaxAge(0);
        response.addCookie(cookie);

        return "login";
    }

}
