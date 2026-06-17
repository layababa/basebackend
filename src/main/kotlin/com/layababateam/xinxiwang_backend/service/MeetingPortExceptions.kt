package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.dto.MeetingDto

class MeetingPasswordRequiredPortException(
    message: String = "该会议需要密码"
) : RuntimeException(message)

class MeetingPasswordIncorrectPortException(
    message: String = "会议密码错误"
) : RuntimeException(message)

class ActiveCreatorMeetingExistsPortException(
    val meeting: MeetingDto
) : RuntimeException("当前已有一个会议正在进行中")

class CreatorScheduledMeetingExistsPortException(
    val meeting: MeetingDto,
    message: String = "当前已有预约宣讲会"
) : RuntimeException(message)
