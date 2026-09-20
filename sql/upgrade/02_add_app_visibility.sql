-- ================================================================
-- 需求3：应用可见范围（私有 / 公开）
-- 为 app 表增加「可见范围」字段
--   private = 私有（默认）：仅创建者与管理员可访问
--   public  = 公开：可被其他用户访问，并进入应用广场与精选列表
-- 老数据兼容：已上精选（priority >= 99）的应用统一置为公开，
--            避免升级后精选列表与应用广场数据凭空减少
-- ================================================================
alter table app
    add column visibility varchar(32) default 'private' not null comment '可见范围：private 私有 / public 公开' after version,
    add index idx_visibility (visibility);

update app
set visibility = 'public'
where priority >= 99
  and visibility <> 'public';
