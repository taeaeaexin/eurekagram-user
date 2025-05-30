package com.gram.eureka.eureka_gram_user.controller;

import com.gram.eureka.eureka_gram_user.dto.BaseResponseDto;
import com.gram.eureka.eureka_gram_user.dto.UserRequestDto;
import com.gram.eureka.eureka_gram_user.dto.UserResponseDto;
import com.gram.eureka.eureka_gram_user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/users")
public class UserController {
    private final UserService userService;

    @PostMapping
    public BaseResponseDto<UserResponseDto> join(@RequestBody UserRequestDto userRequestDto) {
        log.info("회원 가입 입니다. 현재 요청된 데이터는 다음과 같습니다 : {}", userRequestDto);

        /**
         * {
         *   "userName": "서보인",
         *   "email": "sbi1024@naver.com",
         *   "password": "1234",
         *   "nickName": "길동이",
         *   "phoneNumber": "01024685986",
         *   "batch": "FIRST",
         *   "track": "BACKEND",
         *   "mode": "ONLINE"
         * }
         */

        return userService.join(userRequestDto);
    }
}
