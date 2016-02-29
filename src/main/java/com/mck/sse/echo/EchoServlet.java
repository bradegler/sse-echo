package com.mckesson.app.messaging.service;

import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet(urlPatterns={"/sse/echo"}, asyncSupported=true)
public class EchoServlet extends HttpServlet {
    private final static Logger LOGGER = Logger.getLogger(EchoServlet.class.getName());
    private final Queue<AsyncContext> ongoingRequests = new ConcurrentLinkedQueue<>();
    private final ScheduledExecutorService _executor = Executors.newScheduledThreadPool(3);
    
    @Override
    protected void doGet(
            HttpServletRequest req, 
            HttpServletResponse res)
            throws IOException, ServletException {
        
        Map<String, String[]> queryParams = req.getParameterMap();
        String[] ids = queryParams.get("id");
        String[] timeouts = queryParams.get("timeout");
        String[] messages = queryParams.get("message");
        final String id = ids != null && ids.length > 0 ? ids[0] : "Empty";
        final long timeout = timeouts != null && timeouts.length > 0 ? Long.valueOf(timeouts[0]) : 5000;
        final String message = messages != null && messages.length > 0 ? messages[0] : "Dummy";
        
        LOGGER.logp(Level.INFO,"EchoServlet", "doGet",  "[" + id + "] Opening SSE connection - timeout=" + timeout + " message=" + message);
        res.setContentType("text/event-stream");
        res.setCharacterEncoding("UTF-8");

        final AsyncContext ac = req.startAsync();
        ac.setTimeout(timeout + 10000);
        ac.addListener(new AsyncListener(){
            @Override public void onStartAsync(AsyncEvent arg0) throws IOException {}
            @Override public void onComplete(AsyncEvent arg0) throws IOException { 
                LOGGER.logp(Level.INFO,"EchoServlet", "onComplete",  "[" + id + "] completing");
                ongoingRequests.remove(ac); 
            }
            @Override public void onError(AsyncEvent arg0) throws IOException { 
                LOGGER.logp(Level.INFO,"EchoServlet", "onError", "[" + id + "] error", arg0.getThrowable());
                ac.complete(); 
                ongoingRequests.remove(ac); 
            }
            @Override public void onTimeout(AsyncEvent arg0) throws IOException { ac.complete(); ongoingRequests.remove(ac); }
        });

        _executor.schedule(() -> {
                try {
                    final PrintWriter out = ac.getResponse().getWriter();
                    out.write("data: " + message + "\n\n");
                    if (out.checkError()) {
                        LOGGER.logp(Level.SEVERE, "EchoServlet", "run", String.format("Error emitting event to client $s", id));
                    }
                } catch(IOException ioe) {
                    LOGGER.logp(Level.SEVERE, "EchoServlet", "run", String.format("Error emitting event to client $s", id), ioe);
                }
                finally {
                    ac.complete();
                }
        }, timeout, TimeUnit.MILLISECONDS);

    }
}