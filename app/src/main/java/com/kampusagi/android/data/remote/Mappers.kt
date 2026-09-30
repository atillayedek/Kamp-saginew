package com.kampusagi.android.data.remote

import com.kampusagi.android.domain.model.Poll
import com.kampusagi.android.domain.model.PollOption

internal fun PollDto.toDomain() = Poll(
    id = id,
    options = options.map { PollOption(it.id, it.label, it.votes) },
    totalVotes = totalVotes,
    myOptionId = myOptionId,
    closesAt = closesAt,
)
