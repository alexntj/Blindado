package com.passador.replay;

interface IPrivilegedService {
    void destroy() = 16777114;
    void exit() = 1;
    String execute(String command) = 2;
}
