package com.passador.replay;

interface IPrivilegedService {
    void destroy();
    void exit();
    String execute(String command);
}
