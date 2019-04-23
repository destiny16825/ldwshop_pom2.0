package com.qf.utils;

import com.qf.entity.Email;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import javax.mail.internet.MimeMessage;
import java.util.Date;

@Component
public class MailUtil {

    @Autowired
    private JavaMailSender javaMailSender;

    @Value("${spring.mail.username}")
    private String from;

    public void sendMail(Email email){
        //创建邮件
        MimeMessage mimeMessage=javaMailSender.createMimeMessage();
        //创建一个邮件的包装对象
        MimeMessageHelper mimeMessageHelper=new MimeMessageHelper(mimeMessage);

        try {
            //设置发送方
            mimeMessageHelper.setFrom(from,"NBA官方商城");
            //设置接收方
            System.out.println("发送方："+email.getTo());
            mimeMessageHelper.setTo(email.getTo());

            //设置标题
            mimeMessageHelper.setSubject(email.getSubject());
            //设置内容 - 第二个参数表示是否按html解析内容
            mimeMessageHelper.setText(email.getContent(),true);
            //设置发送时间
            mimeMessageHelper.setSentDate(new Date());

        }  catch (Exception e) {
            e.printStackTrace();
        }
        //发送邮件
        javaMailSender.send(mimeMessage);
    }

}
