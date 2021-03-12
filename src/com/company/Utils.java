package com.company;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

public class Utils {

    public static void signApk(String inputName,String outName,String key,String pwd,String alias){
        String keyPath = "./key/" + key;
        String keyPwd = pwd;
        String keyOtherName = alias;
        String out = outName;
        //String cmd = "jarsigner -verbose -keystore 你的签名文件 -storepass 签名文件密码 -signedjar 签名后的apk名称 -digestalg SHA1 -sigalg MD5withRSA 待签名的apk  签名文件别名";
        String cmd = "jarsigner -tsa http://timestamp.digicert.com -verbose -keystore " + keyPath + " -storepass " + keyPwd + " -signedjar " + out + " -digestalg SHA1 -sigalg MD5withRSA " + inputName + "  " + keyOtherName + "";
        Runtime run = Runtime.getRuntime();//返回与当前 Java 应用程序相关的运行时对象
        try {
            Process p = run.exec(cmd);// 启动另一个进程来执行命令
            BufferedInputStream in = new BufferedInputStream(p.getInputStream());
            BufferedReader inBr = new BufferedReader(new InputStreamReader(in));
            String lineStr;
            while ((lineStr = inBr.readLine()) != null)
                //获得命令执行后在控制台的输出信息
                System.out.println(lineStr);// 打印输出信息
            //检查命令是否执行失败。
            if (p.waitFor() != 0) {
                if (p.exitValue() == 1)//p.exitValue()==0表示正常结束，1：非正常结束
                    System.err.println("命令签名失败!");
            }
            inBr.close();
            in.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void signApk(String apkName,String key,String pwd,String alias) {
        String keyPath = "./key/" + key;
        String keyPwd = pwd;
        String keyOtherName = alias;
        String out = apkName.replace(".apk", "_out.apk");
        //String cmd = "jarsigner -verbose -keystore 你的签名文件 -storepass 签名文件密码 -signedjar 签名后的apk名称 -digestalg SHA1 -sigalg MD5withRSA 待签名的apk  签名文件别名";
        String cmd = "jarsigner -tsa http://timestamp.digicert.com -verbose -keystore " + keyPath + " -storepass " + keyPwd + " -signedjar " + out + " -digestalg SHA1 -sigalg MD5withRSA " + apkName + "  " + keyOtherName + "";
        Runtime run = Runtime.getRuntime();//返回与当前 Java 应用程序相关的运行时对象
        try {
            Process p = run.exec(cmd);// 启动另一个进程来执行命令
            BufferedInputStream in = new BufferedInputStream(p.getInputStream());
            BufferedReader inBr = new BufferedReader(new InputStreamReader(in));
            String lineStr;
            while ((lineStr = inBr.readLine()) != null)
                //获得命令执行后在控制台的输出信息
                System.out.println(lineStr);// 打印输出信息
            //检查命令是否执行失败。
            if (p.waitFor() != 0) {
                if (p.exitValue() == 1)//p.exitValue()==0表示正常结束，1：非正常结束
                    System.err.println("命令签名失败!");
            }
            inBr.close();
            in.close();
            //删除掉未签名文件
            File file = (new File(apkName)).getCanonicalFile();
            file.delete();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
