-- Managed OTP providers (Message Central VerifyNow) generate the code themselves:
-- store their verification id instead of a code hash.
alter table otp_challenge add column provider_ref varchar(64);
alter table otp_challenge alter column code_hash drop not null;
