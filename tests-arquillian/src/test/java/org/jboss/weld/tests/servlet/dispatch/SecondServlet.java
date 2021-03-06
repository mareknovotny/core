package org.jboss.weld.tests.servlet.dispatch;

import java.io.IOException;

import jakarta.inject.Inject;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@SuppressWarnings("serial")
@WebServlet("/content")
public class SecondServlet extends HttpServlet {

    @Inject
    private SecondBean bean;

    @Inject
    private SecondConversationScopedBean conversation;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("text/plain");
        resp.getWriter().write(bean.getValue() + ":" + conversation.getValue());
    }
}
