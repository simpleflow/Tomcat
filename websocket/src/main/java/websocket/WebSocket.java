package websocket;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

@ServerEndpoint("/{username}/{action}/{unid}/{client}")
public class WebSocket {

    private static int onlineCount = 0;
    //private static Map<String, WebSocket> clients = new ConcurrentHashMap<String, WebSocket>();
    private static Map<String, Session> sessionlist = new ConcurrentHashMap<String, Session>();
    //编辑的unid以及对应的session
    private static Map<String, WebSocket> editlist = new ConcurrentHashMap<String, WebSocket>();
    //编辑的unid以及对应的session
    private static Map<String, WebSocket> readlist = new ConcurrentHashMap<String, WebSocket>();

    private Session session;
    private String username;
    private String unid;
    private String action;   //read edit
    private String lockunid;
    private String client;    //client type  微信  IE  firefox chrome等

    /*
        场景:
            同一个多次打开编辑同一个文档时, 当前页面打开有效,原页面自动转至读模式
                通过onOpen判断
                    如何找到原来编辑的那个session发消息转至read状态
                    原来的页面ping时,发面同一个unid,已经有新的session锁定了  怎么判断 同一个用户,同一个unid?
            同一个人多次打开读同一个文档时, 允许多次打开

            如果锁定超过30分钟,是否需要自动解锁,并将用户T下线,转入查看模式?  后续再看
     */
 /*
        result json
        {status:   1 regist   2 renew  3 grab  -1 locked  0 read
         msg:      regist  renew  grab  locked read
         to:       消息发送给(从客户端传过来) 这里应该可以不用
         readers:  当前页面的当前读者
         editors:  当前页面的编辑者,锁定者
         client:   锁定者的客户端类型
        }
         */
    @OnOpen
    public void onOpen(@PathParam("action") String action,@PathParam("username") String username,@PathParam("client") String client,
                       @PathParam("unid") String unid, Session session) throws IOException {

        this.username = username;
        this.session = session;
        this.unid = unid;
        this.action = action;
        this.client = client;


        JSONObject result = new JSONObject();

        addOnlineCount();

        if (action.equals("read")){
            result.put("status","0");
            //此处返回读者清单
            //此处返回编辑者清单
            if(editlist.get(unid)!=null){
                result.put("editors",editlist.get(unid).username);
                result.put("client",editlist.get(unid).client);
            }else{
                result.put("editors","");
                result.put("client","");
            }


        }else if (action.equals("edit")){
            if (editlist.containsKey(unid)){
                result.put("status","-1");  //已被锁定
                if(editlist.get(unid)!=null){
                    result.put("editors",editlist.get(unid).username);
                    result.put("client",editlist.get(unid).client);
                }else{
                    result.put("editors","");
                    result.put("client","");
                }

            }else{
                //如果此unid还未锁定,则占用此锁
                this.lockunid = unid;
                System.out.println("锁定:"+this.lockunid+" - "+this.username+" "+this.client);
                editlist.put(unid,this);

                //占用编辑锁时,不再返回当前编辑者
                result.put("editors","");
                result.put("client","");
                result.put("status","1");
            }

        }
        //onOpen时记录所有session清单
        sessionlist.put(session.getId(), session);

        session.getAsyncRemote().sendText(result.toJSONString());
        System.out.println(result.toJSONString());
        System.out.println("已连接 - "+session.getId()+" "+username+" counts:"+getOnlineCount()+" sessions:"+sessionlist.size());
    }

    @OnClose
    public void onClose() throws IOException {

        if (this.lockunid !=null) {
            System.out.println("解锁:"+this.lockunid+" - "+this.username);
            editlist.remove(this.lockunid);
        }
        sessionlist.remove(this.session.getId());
        subOnlineCount();
        System.out.println("已断开 - "+this.session.getId()+" "+this.lockunid+" "+this.username+" counts:"+getOnlineCount()+" sessions:"+sessionlist.size());
        //System.out.println("已断开 - "+this.username+" counts:"+getOnlineCount()+"clients"+clients.size());
    }

    @OnMessage
    public void onMessage(String msg,Session session) throws IOException {

        System.out.println("接收到 - "+session.getId()+" "+this.username+" counts:"+getOnlineCount()+" sessions:"+sessionlist.size()+" "+msg);
        JSONObject result = new JSONObject();
        JSONObject json = JSON.parseObject(msg);

        result.put("status","0");  //只有建立连接时,才判断是否要锁定,onmessage时,连接已经建立,锁资源已经处理
        result.put("editors","");
        result.put("client","");

        if(editlist.get(unid)!=null){
            result.put("debug","editlist not null"+json.getString("action"));
            if(!json.getString("action").equals("edit")){
                //如果当前就是编辑状态,则不再回传当前编辑人
                result.put("editors",editlist.get(unid).username);
                result.put("client",editlist.get(unid).client);
            }
        }


        session.getAsyncRemote().sendText(result.toJSONString());
        /*
        if (!jsonTo.get("To").equals("All")){
            sendMessageTo("给一个人", jsonTo.get("To").toString());
        }else{
            sendMessageAll("给所有人");
        }
        */
    }

    @OnError
    public void onError(Session session, Throwable error) {
        System.out.println("连接异常 - "+session.getId()+" counts:"+getOnlineCount());
        error.printStackTrace();
    }

    public void sendMessageTo(String message, String To) throws IOException {
        // session.getBasicRemote().sendText(message);
        //session.getAsyncRemote().sendText(message);
/*
        for (WebSocket item : clients.values()) {
            if (item.username.equals(To) )
                item.session.getAsyncRemote().sendText(message);
        }

 */
    }

    public void sendMessageAll(String message) throws IOException {
        /*
        for (WebSocket item : clients.values()) {
            item.session.getAsyncRemote().sendText(message);
        }

         */
    }



    public static synchronized int getOnlineCount() {
        return onlineCount;
    }

    public static synchronized void addOnlineCount() {
        WebSocket.onlineCount++;
    }

    public static synchronized void subOnlineCount() {
        WebSocket.onlineCount--;
    }

    public static synchronized Map<String, Session> getSessionlist() {
        return sessionlist;
    }
}