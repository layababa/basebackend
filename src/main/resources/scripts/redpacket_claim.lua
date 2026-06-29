-- KEYS[1] = rentmsg:redpacket:{rp:<id>}
-- KEYS[2] = rentmsg:redpacket:{rp:<id>}:claimed
-- ARGV[1] = userId
-- ARGV[2] = userName
-- ARGV[3] = currentTimeMillis
-- Returns: JSON string

local key = KEYS[1]
local claimedKey = KEYS[2]
local userId = ARGV[1]
local now = tonumber(ARGV[3])

-- 1. Check existence
if redis.call('EXISTS', key) == 0 then
    return '{"success":false,"error":"红包不存在"}'
end

-- 2. Check expiration
local expiredAt = tonumber(redis.call('HGET', key, 'expired_at'))
if expiredAt and now > expiredAt then
    return '{"success":false,"error":"红包已过期"}'
end

-- 3. Check remaining count
local remaining = tonumber(redis.call('HGET', key, 'remaining_count'))
if not remaining or remaining <= 0 then
    return '{"success":false,"error":"红包已领完"}'
end

-- 4. Check if already claimed
if redis.call('SISMEMBER', claimedKey, userId) == 1 then
    return '{"success":false,"error":"您已领取过该红包"}'
end

-- 5. Check targeted red packet
local targetUserId = redis.call('HGET', key, 'target_user_id')
if targetUserId and targetUserId ~= '' and targetUserId ~= userId then
    return '{"success":false,"error":"该红包不是发给您的"}'
end

-- 6. Calculate amount
local rpType = tonumber(redis.call('HGET', key, 'type')) or 0
local remainingAmount = tonumber(redis.call('HGET', key, 'remaining_amount'))
local amount = 0

if remaining == 1 then
    -- Last one gets all remaining
    amount = remainingAmount
elseif rpType == 2 then
    -- Equal split
    local total = tonumber(redis.call('HGET', key, 'total_amount'))
    local count = tonumber(redis.call('HGET', key, 'count'))
    amount = math.floor(total / count * 100) / 100
else
    -- Lucky draw (double-average method)
    local avg = remainingAmount / remaining
    local maxVal = avg * 2
    math.randomseed(now + tonumber(string.sub(userId, -4), 16))
    amount = math.floor(math.random() * maxVal * 100) / 100
    if amount < 0.01 then amount = 0.01 end
    if amount > remainingAmount - (remaining - 1) * 0.01 then
        amount = remainingAmount - (remaining - 1) * 0.01
    end
end

-- 7. Atomic deduction
redis.call('HINCRBY', key, 'remaining_count', -1)
redis.call('HINCRBYFLOAT', key, 'remaining_amount', -amount)
redis.call('SADD', claimedKey, userId)

local newCount = tonumber(redis.call('HGET', key, 'remaining_count'))
local newAmount = tonumber(redis.call('HGET', key, 'remaining_amount'))
-- Fix floating point
if newAmount < 0 then newAmount = 0 end

return string.format('{"success":true,"amount":"%.2f","remainingCount":%d,"remainingAmount":"%.2f"}',
    amount, newCount, newAmount)
