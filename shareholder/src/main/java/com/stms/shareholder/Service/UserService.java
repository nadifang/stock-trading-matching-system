package com.stms.shareholder.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.stms.shareholder.domain.dto.LoginDTO;
import com.stms.shareholder.domain.dto.Result;
import com.stms.shareholder.domain.entity.User;
import com.stms.shareholder.domain.vo.ShareholderVO;

import javax.servlet.http.HttpSession;
import java.util.List;

public interface UserService extends IService<User> {
    Result login(LoginDTO loginForm);

}
