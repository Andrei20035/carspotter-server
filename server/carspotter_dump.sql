--
-- PostgreSQL database dump
--

\restrict PMKG2xYh0GhwufsyBtj68cmnN3IC3jDjN6Zwx14LO2OLdL11WgQaAAXC02dKeRO

-- Dumped from database version 15.16 (Debian 15.16-1.pgdg13+1)
-- Dumped by pg_dump version 15.16 (Debian 15.16-1.pgdg13+1)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

ALTER TABLE IF EXISTS ONLY public.users_cars DROP CONSTRAINT IF EXISTS users_cars_user_id_fkey;
ALTER TABLE IF EXISTS ONLY public.users_cars DROP CONSTRAINT IF EXISTS users_cars_car_model_id_fkey;
ALTER TABLE IF EXISTS ONLY public.users DROP CONSTRAINT IF EXISTS users_auth_credential_id_fkey;
ALTER TABLE IF EXISTS ONLY public.reports DROP CONSTRAINT IF EXISTS reports_reporter_id_fkey;
ALTER TABLE IF EXISTS ONLY public.reports DROP CONSTRAINT IF EXISTS reports_post_id_fkey;
ALTER TABLE IF EXISTS ONLY public.posts DROP CONSTRAINT IF EXISTS posts_user_id_fkey;
ALTER TABLE IF EXISTS ONLY public.posts DROP CONSTRAINT IF EXISTS posts_car_model_id_fkey;
ALTER TABLE IF EXISTS ONLY public.likes DROP CONSTRAINT IF EXISTS likes_user_id_fkey;
ALTER TABLE IF EXISTS ONLY public.likes DROP CONSTRAINT IF EXISTS likes_post_id_fkey;
ALTER TABLE IF EXISTS ONLY public.leaderboard_rank_snapshots DROP CONSTRAINT IF EXISTS leaderboard_rank_snapshots_user_id_fkey;
ALTER TABLE IF EXISTS ONLY public.friends DROP CONSTRAINT IF EXISTS friends_user_id_2_fkey;
ALTER TABLE IF EXISTS ONLY public.friends DROP CONSTRAINT IF EXISTS friends_user_id_1_fkey;
ALTER TABLE IF EXISTS ONLY public.friend_requests DROP CONSTRAINT IF EXISTS friend_requests_sender_id_fkey;
ALTER TABLE IF EXISTS ONLY public.friend_requests DROP CONSTRAINT IF EXISTS friend_requests_receiver_id_fkey;
ALTER TABLE IF EXISTS ONLY public.comments DROP CONSTRAINT IF EXISTS comments_user_id_fkey;
ALTER TABLE IF EXISTS ONLY public.comments DROP CONSTRAINT IF EXISTS comments_post_id_fkey;
ALTER TABLE IF EXISTS ONLY public.auth_sessions DROP CONSTRAINT IF EXISTS auth_sessions_user_id_fkey;
ALTER TABLE IF EXISTS ONLY public.auth_sessions DROP CONSTRAINT IF EXISTS auth_sessions_credential_id_fkey;
DROP TRIGGER IF EXISTS set_users_cars_updated_at ON public.users_cars;
DROP TRIGGER IF EXISTS set_updated_at ON public.posts;
DROP INDEX IF EXISTS public.uq_users_early_spotter_number;
DROP INDEX IF EXISTS public.uq_sessions_refresh_hash;
DROP INDEX IF EXISTS public.uq_active_session_per_credential;
DROP INDEX IF EXISTS public.idx_users_username_lower;
DROP INDEX IF EXISTS public.idx_sessions_prev_hash;
DROP INDEX IF EXISTS public.idx_sessions_credential;
DROP INDEX IF EXISTS public.idx_reports_post;
DROP INDEX IF EXISTS public.idx_rank_snapshots_user_date;
DROP INDEX IF EXISTS public.idx_posts_user_source_created;
DROP INDEX IF EXISTS public.idx_posts_user_created_id;
DROP INDEX IF EXISTS public.idx_posts_user_created;
DROP INDEX IF EXISTS public.idx_posts_feed_keyset;
DROP INDEX IF EXISTS public.idx_posts_created;
DROP INDEX IF EXISTS public.idx_likes_post;
DROP INDEX IF EXISTS public.idx_friends_user_2;
DROP INDEX IF EXISTS public.idx_friend_requests_receiver;
DROP INDEX IF EXISTS public.idx_comments_user;
DROP INDEX IF EXISTS public.idx_comments_post_created;
DROP INDEX IF EXISTS public.flyway_schema_history_s_idx;
ALTER TABLE IF EXISTS ONLY public.users DROP CONSTRAINT IF EXISTS users_pkey;
ALTER TABLE IF EXISTS ONLY public.users_cars DROP CONSTRAINT IF EXISTS users_cars_user_id_key;
ALTER TABLE IF EXISTS ONLY public.users_cars DROP CONSTRAINT IF EXISTS users_cars_pkey;
ALTER TABLE IF EXISTS ONLY public.users DROP CONSTRAINT IF EXISTS users_auth_credential_id_key;
ALTER TABLE IF EXISTS ONLY public.reports DROP CONSTRAINT IF EXISTS reports_reporter_id_post_id_reason_key;
ALTER TABLE IF EXISTS ONLY public.reports DROP CONSTRAINT IF EXISTS reports_pkey;
ALTER TABLE IF EXISTS ONLY public.posts DROP CONSTRAINT IF EXISTS posts_pkey;
ALTER TABLE IF EXISTS ONLY public.likes DROP CONSTRAINT IF EXISTS likes_user_id_post_id_key;
ALTER TABLE IF EXISTS ONLY public.likes DROP CONSTRAINT IF EXISTS likes_pkey;
ALTER TABLE IF EXISTS ONLY public.leaderboard_rank_snapshots DROP CONSTRAINT IF EXISTS leaderboard_rank_snapshots_pkey;
ALTER TABLE IF EXISTS ONLY public.friends DROP CONSTRAINT IF EXISTS friends_pkey;
ALTER TABLE IF EXISTS ONLY public.friend_requests DROP CONSTRAINT IF EXISTS friend_requests_pkey;
ALTER TABLE IF EXISTS ONLY public.flyway_schema_history DROP CONSTRAINT IF EXISTS flyway_schema_history_pk;
ALTER TABLE IF EXISTS ONLY public.early_spotter_counter DROP CONSTRAINT IF EXISTS early_spotter_counter_pkey;
ALTER TABLE IF EXISTS ONLY public.comments DROP CONSTRAINT IF EXISTS comments_pkey;
ALTER TABLE IF EXISTS ONLY public.car_models DROP CONSTRAINT IF EXISTS car_models_pkey;
ALTER TABLE IF EXISTS ONLY public.car_models DROP CONSTRAINT IF EXISTS car_models_brand_model_key;
ALTER TABLE IF EXISTS ONLY public.auth_sessions DROP CONSTRAINT IF EXISTS auth_sessions_pkey;
ALTER TABLE IF EXISTS ONLY public.auth_credentials DROP CONSTRAINT IF EXISTS auth_credentials_pkey;
ALTER TABLE IF EXISTS ONLY public.auth_credentials DROP CONSTRAINT IF EXISTS auth_credentials_email_key;
DROP TABLE IF EXISTS public.users_cars;
DROP TABLE IF EXISTS public.users;
DROP TABLE IF EXISTS public.reports;
DROP TABLE IF EXISTS public.posts;
DROP TABLE IF EXISTS public.likes;
DROP TABLE IF EXISTS public.leaderboard_rank_snapshots;
DROP TABLE IF EXISTS public.friends;
DROP TABLE IF EXISTS public.friend_requests;
DROP TABLE IF EXISTS public.flyway_schema_history;
DROP TABLE IF EXISTS public.early_spotter_counter;
DROP TABLE IF EXISTS public.comments;
DROP TABLE IF EXISTS public.car_models;
DROP TABLE IF EXISTS public.auth_sessions;
DROP TABLE IF EXISTS public.auth_credentials;
DROP FUNCTION IF EXISTS public.update_updated_at_column();
DROP EXTENSION IF EXISTS pgcrypto;
--
-- Name: pgcrypto; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public;


--
-- Name: EXTENSION pgcrypto; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION pgcrypto IS 'cryptographic functions';


--
-- Name: update_updated_at_column(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.update_updated_at_column() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: auth_credentials; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.auth_credentials (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    email character varying(255) NOT NULL,
    password text,
    provider character varying(20) DEFAULT 'REGULAR'::character varying NOT NULL,
    google_id text,
    CONSTRAINT provider_consistency_check CHECK (((((provider)::text = 'REGULAR'::text) AND (google_id IS NULL) AND (password IS NOT NULL)) OR (((provider)::text = 'GOOGLE'::text) AND (google_id IS NOT NULL) AND (password IS NULL))))
);


--
-- Name: auth_sessions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.auth_sessions (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    credential_id uuid NOT NULL,
    user_id uuid,
    version integer DEFAULT 1 NOT NULL,
    scope character varying(20) DEFAULT 'FULL'::character varying NOT NULL,
    refresh_token_hash character(64) NOT NULL,
    prev_token_hash character(64),
    prev_rotated_at timestamp with time zone,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    revoked_reason character varying(40),
    device_id character varying(128),
    device_name character varying(128),
    user_agent text,
    ip_address character varying(64),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    last_used_at timestamp with time zone DEFAULT now() NOT NULL,
    idle_expires_at timestamp with time zone NOT NULL,
    absolute_expires_at timestamp with time zone NOT NULL,
    CONSTRAINT chk_revoked_reason CHECK ((((status)::text <> 'REVOKED'::text) OR ((revoked_reason)::text = ANY ((ARRAY['SUPERSEDED'::character varying, 'LOGOUT'::character varying, 'LOGOUT_ALL'::character varying, 'PASSWORD_CHANGED'::character varying, 'ACCOUNT_DELETED'::character varying, 'REFRESH_TOKEN_REUSED'::character varying, 'IDLE_EXPIRED'::character varying, 'ABSOLUTE_EXPIRED'::character varying])::text[])))),
    CONSTRAINT chk_session_full_has_user CHECK ((((scope)::text <> 'FULL'::text) OR (user_id IS NOT NULL))),
    CONSTRAINT chk_session_scope CHECK (((scope)::text = ANY ((ARRAY['ONBOARDING'::character varying, 'FULL'::character varying])::text[]))),
    CONSTRAINT chk_session_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'REVOKED'::character varying, 'EXPIRED'::character varying])::text[])))
);


--
-- Name: car_models; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.car_models (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    brand character varying(50) NOT NULL,
    model character varying(50) NOT NULL
);


--
-- Name: comments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.comments (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    post_id uuid NOT NULL,
    comment_text text NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT chk_comment_text_max_length CHECK ((length(comment_text) <= 1000)),
    CONSTRAINT chk_comment_text_not_blank CHECK ((length(TRIM(BOTH FROM comment_text)) > 0))
);


--
-- Name: early_spotter_counter; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.early_spotter_counter (
    id smallint DEFAULT 1 NOT NULL,
    last_assigned integer DEFAULT 0 NOT NULL,
    CONSTRAINT chk_last_assigned_range CHECK (((last_assigned >= 0) AND (last_assigned <= 1000))),
    CONSTRAINT chk_single_row CHECK ((id = 1))
);


--
-- Name: flyway_schema_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.flyway_schema_history (
    installed_rank integer NOT NULL,
    version character varying(50),
    description character varying(200) NOT NULL,
    type character varying(20) NOT NULL,
    script character varying(1000) NOT NULL,
    checksum integer,
    installed_by character varying(100) NOT NULL,
    installed_on timestamp without time zone DEFAULT now() NOT NULL,
    execution_time integer NOT NULL,
    success boolean NOT NULL
);


--
-- Name: friend_requests; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.friend_requests (
    sender_id uuid NOT NULL,
    receiver_id uuid NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT chk_no_self_friend_request CHECK ((sender_id <> receiver_id))
);


--
-- Name: friends; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.friends (
    user_id_1 uuid NOT NULL,
    user_id_2 uuid NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT chk_friend_pair_order CHECK ((user_id_1 < user_id_2)),
    CONSTRAINT chk_no_self_friendship CHECK ((user_id_1 <> user_id_2))
);


--
-- Name: leaderboard_rank_snapshots; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.leaderboard_rank_snapshots (
    snapshot_date date NOT NULL,
    user_id uuid NOT NULL,
    rank integer NOT NULL,
    spot_score integer NOT NULL
);


--
-- Name: likes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.likes (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    post_id uuid NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: posts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.posts (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    car_model_id uuid,
    custom_brand character varying(50),
    custom_model character varying(80),
    image_path text NOT NULL,
    description text,
    latitude double precision,
    longitude double precision,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    town character varying(100),
    country character varying(100),
    source character varying(16) DEFAULT 'GALLERY'::character varying NOT NULL,
    created_at_timezone character varying(64),
    points integer DEFAULT 0 NOT NULL,
    CONSTRAINT chk_latitude CHECK (((latitude IS NULL) OR ((latitude >= ('-90'::integer)::double precision) AND (latitude <= (90)::double precision)))),
    CONSTRAINT chk_longitude CHECK (((longitude IS NULL) OR ((longitude >= ('-180'::integer)::double precision) AND (longitude <= (180)::double precision)))),
    CONSTRAINT chk_post_car_source CHECK ((((car_model_id IS NOT NULL) AND (custom_brand IS NULL) AND (custom_model IS NULL)) OR ((car_model_id IS NULL) AND (custom_brand IS NOT NULL) AND (custom_model IS NOT NULL)))),
    CONSTRAINT chk_post_points_non_negative CHECK ((points >= 0)),
    CONSTRAINT chk_posts_custom_brand_not_blank CHECK (((custom_brand IS NULL) OR (length(TRIM(BOTH FROM custom_brand)) > 0))),
    CONSTRAINT chk_posts_custom_model_not_blank CHECK (((custom_model IS NULL) OR (length(TRIM(BOTH FROM custom_model)) > 0))),
    CONSTRAINT chk_posts_description_max_length CHECK (((description IS NULL) OR (length(description) <= 1000))),
    CONSTRAINT chk_posts_description_not_blank CHECK (((description IS NULL) OR (length(TRIM(BOTH FROM description)) > 0))),
    CONSTRAINT chk_posts_location_pair CHECK ((((latitude IS NULL) AND (longitude IS NULL)) OR ((latitude IS NOT NULL) AND (longitude IS NOT NULL))))
);


--
-- Name: reports; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.reports (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    reporter_id uuid NOT NULL,
    post_id uuid NOT NULL,
    reason character varying(30) NOT NULL,
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.users (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    auth_credential_id uuid NOT NULL,
    profile_picture_path text,
    full_name character varying(150) NOT NULL,
    phone_number character varying(20),
    birth_date date NOT NULL,
    username character varying(50) NOT NULL,
    country character varying(50) NOT NULL,
    spot_score integer DEFAULT 0 NOT NULL,
    role character varying(20) DEFAULT 'USER'::character varying NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    current_streak integer DEFAULT 0 NOT NULL,
    longest_streak integer DEFAULT 0 NOT NULL,
    last_streak_date date,
    last_streak_timezone character varying(64),
    is_early_spotter boolean DEFAULT false NOT NULL,
    early_spotter_number integer,
    CONSTRAINT chk_birth_date_valid CHECK ((birth_date <= CURRENT_DATE)),
    CONSTRAINT chk_early_spotter_consistency CHECK ((((is_early_spotter = true) AND (early_spotter_number IS NOT NULL)) OR ((is_early_spotter = false) AND (early_spotter_number IS NULL)))),
    CONSTRAINT chk_early_spotter_number_range CHECK (((early_spotter_number IS NULL) OR ((early_spotter_number >= 1) AND (early_spotter_number <= 1000)))),
    CONSTRAINT chk_spot_score_non_negative CHECK ((spot_score >= 0)),
    CONSTRAINT chk_user_role CHECK (((role)::text = ANY ((ARRAY['USER'::character varying, 'ADMIN'::character varying])::text[]))),
    CONSTRAINT chk_username_not_blank CHECK ((length(TRIM(BOTH FROM username)) > 0))
);


--
-- Name: users_cars; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.users_cars (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    car_model_id uuid,
    custom_brand character varying(50),
    custom_model character varying(80),
    image_path text NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT chk_user_car_source CHECK ((((car_model_id IS NOT NULL) AND (custom_brand IS NULL) AND (custom_model IS NULL)) OR ((car_model_id IS NULL) AND (custom_brand IS NOT NULL) AND (custom_model IS NOT NULL)))),
    CONSTRAINT chk_users_cars_custom_brand_not_blank CHECK (((custom_brand IS NULL) OR (length(TRIM(BOTH FROM custom_brand)) > 0))),
    CONSTRAINT chk_users_cars_custom_model_not_blank CHECK (((custom_model IS NULL) OR (length(TRIM(BOTH FROM custom_model)) > 0))),
    CONSTRAINT chk_users_cars_image_path_not_blank CHECK ((length(TRIM(BOTH FROM image_path)) > 0))
);


--
-- Data for Name: auth_credentials; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.auth_credentials (id, email, password, provider, google_id) FROM stdin;
e77994e2-84c7-405e-bdfb-056009da2b51	test1@gmail.com	$2a$12$UMGkIOmd1HkSkDoZAfVL6ORZNhUYDpCR01nDvQhnJfNWHi3jCxXAq	REGULAR	\N
a9ac4a4a-8df5-46dc-996e-086638a05c30	test2@gmail.com	$2a$12$POcQOqfUBLtpE8/f7irvNeOLEbjKNmULQtpa/JAWA6I4RZMwcWHLK	REGULAR	\N
95596c63-670c-4eac-917c-fdcc5fab98e0	test3@gmail.com	$2a$12$zF/m5KJ.kqO/YdJNq4SWheT.J42Ca2MY2XsVthJg6TXguVOORLXue	REGULAR	\N
eaa28bb7-534f-4913-9d4f-1f8736efca32	test4@gmail.com	$2a$12$nNj.bsfUpHv0OIhKu40JW.TA6zJuOxnd/EPzBoUM568pEYkez1zcq	REGULAR	\N
f543d3f0-5da3-4d15-a976-febbb8bf0083	test5@gmail.com	$2a$12$fI71UHE5aDWz4bGYfg8T0uu60WZahPPYIEl47xRHzeNF82qCT5/Ra	REGULAR	\N
e02522de-a128-4824-b014-375c96477f32	test6@gmail.com	$2a$12$QuPWr4wrgPQ5R.uRMqKn2..O6tO/eC5PaajeHdsO9LZoT8s.daQme	REGULAR	\N
7b027453-1ede-48ec-a942-b260d7c37494	test7@gmail.com	$2a$12$CUfsV4S/yUPOHdXgwRjiOeOKn3FBrtfqlAZeRaJoBEaupYfSDJKx2	REGULAR	\N
beb61e4d-752f-4d36-9174-3e443862df55	test8@gmail.com	$2a$12$YPmq7YCKlXtGUKzFmMMKQuO4LgnRRLXB3SgUie2epYdF.SGQwIBVG	REGULAR	\N
117991ef-d6b1-43b5-8e9d-ef8edf978574	test9@gmail.com	$2a$12$o.7KZd/bAFZ/WJjr3Q9aVepo7WXyUSa7.4WgF8rwOug.zC65MLKe.	REGULAR	\N
8f3d29f4-9ff3-4f3e-8da5-c3c7571e9036	test10@gmail.com	$2a$12$3yTrRQjDNnrQe491nrghU.YuJkiMame71r0VTg5rA1L7uEfWTzabu	REGULAR	\N
0ef9a8f5-94f1-4bbf-bd10-bce6532251ea	amrusu2@gmail.com	\N	GOOGLE	101424912003816577935
2ec3c0e1-565e-44a7-811f-ce0ef0727b82	socate1@gmail.com	$2a$12$wAIrn1JowW2hoznJBE23HeTQ6jOuBwY4.Dfky7b6g4FPNxAkvScfq	REGULAR	\N
db686ac8-05a4-458e-9680-82c1ae8c14b4	socate2@gmail.com	$2a$12$.rYPDXZyqZGMPcJE9EDPJOBPg6UZTKOSbXt0ENbspr1.PDf8kUKdu	REGULAR	\N
0123a39d-41d5-426b-9761-875e5ed2ccb8	socate3@gmail.com	$2a$12$CwW66Q3qEdt09VjmV9AAZOUdBQqYTHkjRs9KJlurZ.AGe8rc2VCHG	REGULAR	\N
\.


--
-- Data for Name: auth_sessions; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.auth_sessions (id, credential_id, user_id, version, scope, refresh_token_hash, prev_token_hash, prev_rotated_at, status, revoked_reason, device_id, device_name, user_agent, ip_address, created_at, last_used_at, idle_expires_at, absolute_expires_at) FROM stdin;
6dad0630-1124-425b-b159-714284bb4211	e77994e2-84c7-405e-bdfb-056009da2b51	aea27264-e0e1-4981-9ffd-104046764c62	1	FULL	67365b926e4178f67c365920840c62a1efaba1d093a35ce93941b92763076d9b	\N	\N	REVOKED	SUPERSEDED	\N	\N	okhttp/5.3.2	192.168.0.107	2026-06-23 22:32:14.849379+00	2026-06-23 22:32:14.849379+00	2026-07-23 22:32:14.847975+00	2026-12-20 22:32:14.847975+00
15dde0a4-2f6d-44fb-a509-966a2a493ab8	0123a39d-41d5-426b-9761-875e5ed2ccb8	\N	1	ONBOARDING	74d4a4e47e7a4fc9c894ae93a70bebe9656ba4818ab0f09910aa5c4294145a36	\N	\N	REVOKED	SUPERSEDED	f53fedf0-2db8-444a-85ea-4a56e25558cc	OnePlus KB2003	okhttp/5.3.2	192.168.1.247	2026-06-27 16:31:29.99257+00	2026-06-27 16:31:29.99257+00	2026-07-27 16:31:29.993162+00	2026-12-24 16:31:29.993162+00
707422bd-25ba-4ee8-b375-8fef261d3d5b	e77994e2-84c7-405e-bdfb-056009da2b51	aea27264-e0e1-4981-9ffd-104046764c62	1	FULL	163041348077d90e1cbed7a8a5da5bcde1374265f74b9f84aa7829e6abc85177	\N	\N	REVOKED	SUPERSEDED	\N	\N	okhttp/5.3.2	192.168.0.107	2026-06-23 22:32:20.754735+00	2026-06-23 22:32:20.754735+00	2026-07-23 22:32:20.755337+00	2026-12-20 22:32:20.755337+00
073518f0-d7f9-427c-b1f1-5455c56aecb7	e77994e2-84c7-405e-bdfb-056009da2b51	aea27264-e0e1-4981-9ffd-104046764c62	1	FULL	e5dd00c7b77a60d64d57630a56fc01ae93c50082e4d6270ad7d474e3636129a8	\N	\N	REVOKED	LOGOUT	fba9cd5f-e743-4680-b91e-651607a56c7f	Google sdk_gphone16k_arm64	okhttp/5.3.2	192.168.0.107	2026-06-23 22:42:17.452737+00	2026-06-23 22:42:17.452737+00	2026-07-23 22:42:17.450941+00	2026-12-20 22:42:17.450941+00
02e06097-9e22-4636-8651-8a32473422f8	95596c63-670c-4eac-917c-fdcc5fab98e0	ab55e2ee-9e0b-493b-9f90-a7bdbfe32f70	1	FULL	e14478801584674dfac5e6c902bc94fa529bbce951346b4e7d1f4406903a8d4f	\N	\N	REVOKED	SUPERSEDED	987980a1-7b0f-40ca-8bc9-4115dbb6b8b5	LGE LG-H930	okhttp/5.3.2	192.168.0.101	2026-06-23 22:45:54.398579+00	2026-06-23 22:45:54.398579+00	2026-07-23 22:45:54.39828+00	2026-12-20 22:45:54.39828+00
aee73de3-5ba9-43a9-bb78-e652a529db77	95596c63-670c-4eac-917c-fdcc5fab98e0	ab55e2ee-9e0b-493b-9f90-a7bdbfe32f70	1	FULL	0c55eb84befaaf1613166311450520783dc36b92978aa4dc193d5963c47caeac	\N	\N	REVOKED	SUPERSEDED	0ba545df-7216-4d1f-aa77-375d48bda553	OnePlus KB2003	okhttp/5.3.2	192.168.0.100	2026-06-23 22:46:34.415023+00	2026-06-23 22:46:34.415023+00	2026-07-23 22:46:34.415243+00	2026-12-20 22:46:34.415243+00
efd70262-8d1f-41df-8b87-1c17d5aa3da3	e77994e2-84c7-405e-bdfb-056009da2b51	aea27264-e0e1-4981-9ffd-104046764c62	1	FULL	2972747dc4ab1c5194059ea7efc5d5d98b9e8fb41f968a7d81865809621bbdf4	\N	\N	REVOKED	LOGOUT	0ba545df-7216-4d1f-aa77-375d48bda553	OnePlus KB2003	okhttp/5.3.2	192.168.0.100	2026-06-23 22:47:31.726135+00	2026-06-23 22:47:31.726135+00	2026-07-23 22:47:31.726613+00	2026-12-20 22:47:31.726613+00
1d0ad3c4-96bc-44b3-bc10-0158f8a47c50	a9ac4a4a-8df5-46dc-996e-086638a05c30	c8b2cbc4-3855-4a2a-b960-a9db534c7c8e	1	FULL	332e808bf928895401457a00f61dea36f41438fa643c91cb90eb191c3f0cc506	\N	\N	REVOKED	LOGOUT	0ba545df-7216-4d1f-aa77-375d48bda553	OnePlus KB2003	okhttp/5.3.2	192.168.0.100	2026-06-23 22:52:15.286113+00	2026-06-23 22:52:15.286113+00	2026-07-23 22:52:15.286571+00	2026-12-20 22:52:15.286571+00
08bda545-32be-467a-b8f0-cb9cdc2512c4	e77994e2-84c7-405e-bdfb-056009da2b51	aea27264-e0e1-4981-9ffd-104046764c62	1	FULL	b012bbc21c4e34e4f6b3c33b4f52cb01fd5e94ed0f0eb5227b0233bfdb3ff39f	\N	\N	REVOKED	LOGOUT	0ba545df-7216-4d1f-aa77-375d48bda553	OnePlus KB2003	okhttp/5.3.2	192.168.0.100	2026-06-23 22:52:44.859855+00	2026-06-23 22:52:44.859855+00	2026-07-23 22:52:44.861002+00	2026-12-20 22:52:44.861002+00
c5ff4d2e-6e68-4573-84e6-413cdf82b2e2	e77994e2-84c7-405e-bdfb-056009da2b51	aea27264-e0e1-4981-9ffd-104046764c62	2	FULL	94b7b8397cd423ea8d3338bb064fbd0d54ae76c3c00808a60e13b513b3c5019a	cfecf946c167e9e0cfdbed0e001b61a3e87ab199c5a57dbc84d76ed0d00cebfc	2026-06-24 14:16:55.729616+00	REVOKED	SUPERSEDED	0ba545df-7216-4d1f-aa77-375d48bda553	OnePlus KB2003	okhttp/5.3.2	192.168.0.100	2026-06-23 23:31:06.358306+00	2026-06-24 14:16:55.729616+00	2026-07-24 14:16:55.729616+00	2026-12-20 23:31:06.358289+00
e16f6b8e-858d-4e18-9fbf-d8bb0814fd80	e77994e2-84c7-405e-bdfb-056009da2b51	aea27264-e0e1-4981-9ffd-104046764c62	3	FULL	4a8d5b48d5a4c4c88b9de57d3855f3c4e80a1ce27f0317f8c561d75908d4a1d1	af48671488768c02c41c0b0f4f521afe27dc7c6fd78648eb4f96d1e5340300e7	2026-06-24 15:39:57.533394+00	REVOKED	SUPERSEDED	fba9cd5f-e743-4680-b91e-651607a56c7f	Google sdk_gphone16k_arm64	okhttp/5.3.2	192.168.0.107	2026-06-24 14:17:11.23223+00	2026-06-24 15:39:57.533394+00	2026-07-24 15:39:57.533394+00	2026-12-21 14:17:11.230958+00
aa032422-c0c5-4ab1-94e7-2d6187e2d9ba	e77994e2-84c7-405e-bdfb-056009da2b51	aea27264-e0e1-4981-9ffd-104046764c62	3	FULL	4c6151bd22aef199474fe24c4d8d4d961debf264a53e1cb4e2a099293154de0f	e42abfc87b58a33729168975a855d298a082dc283d12c5beb8ffb7dba2180796	2026-06-26 12:21:10.926131+00	REVOKED	SUPERSEDED	0c81bcef-7539-4852-807a-3cc500249298	Google sdk_gphone16k_arm64	okhttp/5.3.2	10.164.214.36	2026-06-25 09:50:50.486532+00	2026-06-26 12:21:10.926131+00	2026-07-26 12:21:10.926131+00	2026-12-22 09:50:50.484131+00
1c5ebb23-5d7f-4682-9dbf-f9c73fc17298	e77994e2-84c7-405e-bdfb-056009da2b51	aea27264-e0e1-4981-9ffd-104046764c62	1	FULL	4c6ff7785f0df5fc83052e133fe8df8ea35c1f1271bb93ea00c83a869b322e6c	\N	\N	REVOKED	SUPERSEDED	c868c91d-7e40-4b06-a5b0-4f0ded0e09b3	OnePlus KB2003	okhttp/5.3.2	192.168.1.247	2026-06-26 12:23:47.749986+00	2026-06-26 12:23:47.749986+00	2026-07-26 12:23:47.749665+00	2026-12-23 12:23:47.749665+00
284d389c-2934-4efc-9920-a08258f6b567	a9ac4a4a-8df5-46dc-996e-086638a05c30	c8b2cbc4-3855-4a2a-b960-a9db534c7c8e	4	FULL	9b40c95aa1fdd15a9b05aa96e25c077e5c377dc6facb0d56325719e0c80657d7	05bb97c0cd58ad9bba9df0d30ca15679e6a9eb175ceb24b02540433a961f3e49	2026-06-26 12:22:13.503903+00	REVOKED	SUPERSEDED	0ba545df-7216-4d1f-aa77-375d48bda553	OnePlus KB2003	okhttp/5.3.2	192.168.0.100	2026-06-24 14:19:16.474585+00	2026-06-26 12:22:13.503903+00	2026-07-26 12:22:13.503903+00	2026-12-21 14:19:16.474867+00
dba04b85-c279-49cf-93f4-8204077e65c7	a9ac4a4a-8df5-46dc-996e-086638a05c30	c8b2cbc4-3855-4a2a-b960-a9db534c7c8e	8	FULL	5b62ef09dd5d8314fa06e4510f311e402673afe2289d8f1f6a0b7ba78e6154f9	0b6f2c162ad1d51d39e99079340cb3e103225765b704f2af17b9f5ce752ef49e	2026-06-26 16:52:50.178718+00	REVOKED	SUPERSEDED	c868c91d-7e40-4b06-a5b0-4f0ded0e09b3	OnePlus KB2003	okhttp/5.3.2	192.168.1.247	2026-06-26 12:35:15.793067+00	2026-06-26 16:52:50.178718+00	2026-07-26 16:52:50.178718+00	2026-12-23 12:35:15.791824+00
9e90a4d2-929f-49a6-b0cf-d062b88e62f0	e77994e2-84c7-405e-bdfb-056009da2b51	aea27264-e0e1-4981-9ffd-104046764c62	12	FULL	7badbf54862d06bf3556b8b978740781df9348ecc60629e3fa8892c18808e7cd	da9969dfe1667b115d01c06dd635aa0a646e185db20380c4ff90a0a82398729c	2026-06-27 12:52:39.578864+00	REVOKED	LOGOUT	6325e704-8721-4742-b391-b17601893031	Google sdk_gphone16k_arm64	okhttp/5.3.2	192.168.1.191	2026-06-26 12:24:36.476192+00	2026-06-27 12:52:39.578864+00	2026-07-27 12:52:39.578864+00	2026-12-23 12:24:36.47627+00
4d5a5925-40fb-40f9-80c4-a1254dbe9ad6	a9ac4a4a-8df5-46dc-996e-086638a05c30	c8b2cbc4-3855-4a2a-b960-a9db534c7c8e	4	FULL	36a26fe637b5089a918f9f7fcc9d01d394ce3776d6b4c6f6d43fbf019f4ce721	7806985917b40b7ce5d96a86e2e4a21eb6a1ac385130e9a5d283d75bb05114b5	2026-06-27 12:54:09.018739+00	REVOKED	LOGOUT	027c129b-3ec1-4a79-a9d8-76b54589c228	OnePlus KB2003	okhttp/5.3.2	192.168.1.247	2026-06-26 16:55:56.251441+00	2026-06-27 12:54:09.018739+00	2026-07-27 12:54:09.018739+00	2026-12-23 16:55:56.251766+00
07b20536-ee7d-4c9d-88cd-34c2080852fb	0ef9a8f5-94f1-4bbf-bd10-bce6532251ea	\N	1	ONBOARDING	2ba9ad6359fd9bdd24faa39a0632adb572591bf5a1f08fc80f67c6597bd3be86	\N	\N	ACTIVE	\N	027c129b-3ec1-4a79-a9d8-76b54589c228	OnePlus KB2003	okhttp/5.3.2	192.168.1.247	2026-06-27 12:54:42.609833+00	2026-06-27 12:54:42.609833+00	2026-07-27 12:54:42.610206+00	2026-12-24 12:54:42.610206+00
2bd322ef-0ab1-45c9-96c5-373281470386	2ec3c0e1-565e-44a7-811f-ce0ef0727b82	\N	1	ONBOARDING	da58eab72289aef9638f0434c472cad80936c343e9e48fbe54b4a3a4c47d3fbe	\N	\N	REVOKED	SUPERSEDED	f53fedf0-2db8-444a-85ea-4a56e25558cc	OnePlus KB2003	okhttp/5.3.2	192.168.1.247	2026-06-27 14:59:33.33427+00	2026-06-27 14:59:33.33427+00	2026-07-27 14:59:33.334415+00	2026-12-24 14:59:33.334415+00
49fbdd9f-a1de-4e0d-a186-a19cccb6d47d	2ec3c0e1-565e-44a7-811f-ce0ef0727b82	94077bb6-3204-4818-b312-536d6e88119d	3	FULL	88a1dea1b0ce507986f00f95315c34acc0a36e4031eebc624aec5f0e7b40227a	92b44d140c08e2234b42385c2dae1d45eae12668ae782ab24b3b56976975d4e8	2026-06-27 16:18:20.925652+00	REVOKED	LOGOUT	f53fedf0-2db8-444a-85ea-4a56e25558cc	OnePlus KB2003	okhttp/5.3.2	192.168.1.247	2026-06-27 15:09:14.452898+00	2026-06-27 16:18:20.925652+00	2026-07-27 16:18:20.925652+00	2026-12-24 15:09:14.453286+00
2e69c058-b482-4e1c-b6b0-9a58c8b63407	db686ac8-05a4-458e-9680-82c1ae8c14b4	\N	1	ONBOARDING	6a649825e565e961d4f0392b3e7e04d5a7626b8e9c8b43689eac15391844da68	\N	\N	REVOKED	SUPERSEDED	f53fedf0-2db8-444a-85ea-4a56e25558cc	OnePlus KB2003	okhttp/5.3.2	192.168.1.247	2026-06-27 15:08:27.956713+00	2026-06-27 15:08:27.956713+00	2026-07-27 15:08:27.955234+00	2026-12-24 15:08:27.955234+00
9bea07ca-e552-4735-8d30-56d1df7b24d4	e77994e2-84c7-405e-bdfb-056009da2b51	aea27264-e0e1-4981-9ffd-104046764c62	3	FULL	4e5eff0378d55d74ae63c82038eb6c091d88857bf8530be1cb474181a1c92078	f779b53473e5f0502f2bb7330879745b04b8ad6e43e5ff9154ec3045c5f86436	2026-06-27 16:22:32.140544+00	REVOKED	LOGOUT	6325e704-8721-4742-b391-b17601893031	Google sdk_gphone16k_arm64	okhttp/5.3.2	192.168.1.191	2026-06-27 12:53:24.340617+00	2026-06-27 16:22:32.140544+00	2026-07-27 16:22:32.140544+00	2026-12-24 12:53:24.341042+00
de2422d7-22d8-4fce-a5cb-8baf5f059a57	db686ac8-05a4-458e-9680-82c1ae8c14b4	01a221f6-8491-4d35-a60c-fd5399bc194e	2	FULL	f0ca58f90f22700e096e92c9289fa8c5d8dc523ed8e914d60e844d34e040b5df	a55991062d7e77e98b325626614a7931e0a3c4f2d0bcd90ec7bdbe66703393fa	2026-06-27 16:19:44.868535+00	REVOKED	LOGOUT	f53fedf0-2db8-444a-85ea-4a56e25558cc	OnePlus KB2003	okhttp/5.3.2	192.168.1.247	2026-06-27 16:18:38.814864+00	2026-06-27 16:19:44.868535+00	2026-07-27 16:18:38.814337+00	2026-12-24 16:18:38.814337+00
b4120923-5411-433a-880a-bc4d12ae5020	0123a39d-41d5-426b-9761-875e5ed2ccb8	\N	1	ONBOARDING	dc06660a1de2c7b1fefb63a39269342fcbc682ea7feddd30de894ac9aaee6346	\N	\N	REVOKED	SUPERSEDED	f53fedf0-2db8-444a-85ea-4a56e25558cc	OnePlus KB2003	okhttp/5.3.2	192.168.1.247	2026-06-27 16:26:56.244587+00	2026-06-27 16:26:56.244587+00	2026-07-27 16:26:56.244883+00	2026-12-24 16:26:56.244883+00
bad50d91-f5b6-43af-a8ab-9e00a9b7d6cd	0123a39d-41d5-426b-9761-875e5ed2ccb8	\N	1	ONBOARDING	43c57f2c207257bbfbb890a6de17ebb9ec0cc654f7256c592c1c27027e0b19d1	\N	\N	REVOKED	SUPERSEDED	6325e704-8721-4742-b391-b17601893031	Google sdk_gphone16k_arm64	okhttp/5.3.2	192.168.1.191	2026-06-27 16:23:05.675484+00	2026-06-27 16:23:05.675484+00	2026-07-27 16:23:05.675518+00	2026-12-24 16:23:05.675518+00
da6053ba-012a-4725-885e-a3032d1b31ac	2ec3c0e1-565e-44a7-811f-ce0ef0727b82	94077bb6-3204-4818-b312-536d6e88119d	1	FULL	b59d1157122bb4d697c190f40b830049a49b0a8915ffa2deca09e77d8414672b	\N	\N	REVOKED	LOGOUT	6325e704-8721-4742-b391-b17601893031	Google sdk_gphone16k_arm64	okhttp/5.3.2	192.168.1.191	2026-06-27 16:54:18.498346+00	2026-06-27 16:54:18.498346+00	2026-07-27 16:54:18.4987+00	2026-12-24 16:54:18.4987+00
728a5b22-288e-4644-971e-1476fc0eb9ce	0123a39d-41d5-426b-9761-875e5ed2ccb8	\N	1	ONBOARDING	fd1aa6148178d68172f29155cd83bd03715e7a38b279488b256007b172590e69	\N	\N	REVOKED	SUPERSEDED	f53fedf0-2db8-444a-85ea-4a56e25558cc	OnePlus KB2003	okhttp/5.3.2	192.168.1.247	2026-06-27 16:52:31.485139+00	2026-06-27 16:52:31.485139+00	2026-07-27 16:52:31.484814+00	2026-12-24 16:52:31.484814+00
0ab43182-15b6-464c-b150-2b1e29bc4107	0123a39d-41d5-426b-9761-875e5ed2ccb8	\N	1	ONBOARDING	0db9a964cceca5aa4ff1ebe922261ac65b91543984f589a631c03af6a7e1e077	\N	\N	REVOKED	SUPERSEDED	6325e704-8721-4742-b391-b17601893031	Google sdk_gphone16k_arm64	okhttp/5.3.2	192.168.1.191	2026-06-27 16:54:44.269536+00	2026-06-27 16:54:44.269536+00	2026-07-27 16:54:44.26916+00	2026-12-24 16:54:44.26916+00
60c679d4-fa16-4c61-b211-74b4ec37585a	95596c63-670c-4eac-917c-fdcc5fab98e0	ab55e2ee-9e0b-493b-9f90-a7bdbfe32f70	8	FULL	2841c9a8c4966d447c1b0c54458c437d1de05fe69cf6e784d6d279ee71d35731	ce029e325ae24df41b9501e409efa3f1d4af45f76bc9603a99a3de20f3c5e3cd	2026-06-27 17:01:47.24069+00	REVOKED	LOGOUT	987980a1-7b0f-40ca-8bc9-4115dbb6b8b5	LGE LG-H930	okhttp/5.3.2	192.168.0.101	2026-06-23 22:47:01.318053+00	2026-06-27 17:01:47.24069+00	2026-07-27 17:01:47.24069+00	2026-12-20 22:47:01.318349+00
59346723-3b03-4a4c-9fa9-9f42725b1d49	2ec3c0e1-565e-44a7-811f-ce0ef0727b82	94077bb6-3204-4818-b312-536d6e88119d	1	FULL	fe18f0302e3c84b19cc1e8ed260fee1cda0d2f5d736cd2d7fcbc99d58effba22	\N	\N	REVOKED	LOGOUT	987980a1-7b0f-40ca-8bc9-4115dbb6b8b5	LGE LG-H930	okhttp/5.3.2	192.168.1.242	2026-06-27 17:03:00.844645+00	2026-06-27 17:03:00.844645+00	2026-07-27 17:03:00.846202+00	2026-12-24 17:03:00.846202+00
49cdc69b-ff4f-40bd-ac4b-c4e4b7e863d3	0123a39d-41d5-426b-9761-875e5ed2ccb8	\N	1	ONBOARDING	2f0423af2efa2fbdd81835a7bf7dec6fe5637f09bfb8fc474ecaaa4b0931b825	\N	\N	REVOKED	SUPERSEDED	6325e704-8721-4742-b391-b17601893031	Google sdk_gphone16k_arm64	okhttp/5.3.2	192.168.1.191	2026-06-27 17:01:23.641183+00	2026-06-27 17:01:23.641183+00	2026-07-27 17:01:23.640387+00	2026-12-24 17:01:23.640387+00
acdc656f-84d5-432b-b169-0e16afaa0b3d	0123a39d-41d5-426b-9761-875e5ed2ccb8	\N	1	ONBOARDING	13aaf2f1e15a459e0890f5bafeffc0d22841924fc57278729b5c2a6fd005ac25	\N	\N	REVOKED	SUPERSEDED	987980a1-7b0f-40ca-8bc9-4115dbb6b8b5	LGE LG-H930	okhttp/5.3.2	192.168.1.242	2026-06-27 17:03:24.08686+00	2026-06-27 17:03:24.08686+00	2026-07-27 17:03:24.088363+00	2026-12-24 17:03:24.088363+00
8e00d891-3702-4b98-8bef-b7a77a1ef1dd	0123a39d-41d5-426b-9761-875e5ed2ccb8	\N	1	ONBOARDING	7fc28977f28896a9e2adfaeb06ae6fdcc2acbedfac8b104ae9e301a77f4497f4	\N	\N	REVOKED	SUPERSEDED	987980a1-7b0f-40ca-8bc9-4115dbb6b8b5	LGE LG-H930	okhttp/5.3.2	192.168.1.242	2026-06-27 17:05:43.829254+00	2026-06-27 17:05:43.829254+00	2026-07-27 17:05:43.829225+00	2026-12-24 17:05:43.829225+00
974867e9-e0b8-4d45-9f2d-40953f3e4029	0123a39d-41d5-426b-9761-875e5ed2ccb8	\N	1	ONBOARDING	539cb02fcc405fef5f79dc6a6f8dfe3b17a25fd97658f2b4877354f9150557d7	\N	\N	ACTIVE	\N	987980a1-7b0f-40ca-8bc9-4115dbb6b8b5	LGE LG-H930	okhttp/5.3.2	192.168.1.242	2026-06-27 17:09:17.558531+00	2026-06-27 17:09:17.558531+00	2026-07-27 17:09:17.559023+00	2026-12-24 17:09:17.559023+00
\.


--
-- Data for Name: car_models; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.car_models (id, brand, model) FROM stdin;
8f324533-5098-46a8-84fa-871072a8ba83	AC	Ace
952d8031-3dae-4f4c-bce8-4d5cf17f5c67	AC	Aceca
3a68095b-f274-44fe-bc40-f9fb52daf12c	AC	Cobra
1748d63f-4d3e-4656-90bb-a74ea6ddb873	AC	Frua Convertible
0c4a29d7-43ad-49ea-800d-985ee0bc11ba	AC	Frua Coupe
40dcf445-b151-4cff-b2e7-a56a518f21c3	Acura	ADX
9c70f1d1-2a89-4534-ad53-f44ff668a7cc	Acura	CL
39b109b6-ca28-4518-b26c-9f2d5af282d4	Acura	CSX
3addd218-c39f-4ace-945e-c46dace4e0bc	Acura	EL
a3d4de50-f9fe-44c7-88ec-95cf5323195c	Acura	ILX
24b72b1b-fe26-4f98-9a29-0667b7bac00c	Acura	Integra Coupe
1fb69eed-62d6-46e5-9eac-0e126dd4cab3	Acura	Integra Sedan
18359276-220e-4a4c-b00a-a4642b4df855	Acura	Legend
142a6b4d-e036-4404-b36c-c9854bf13ada	Acura	Legend Coupe
3dba9bd5-56b9-4243-b308-9c8999dc42f6	Acura	MDX
f3b918f6-fe65-4c87-9033-f78a9802e57d	Acura	NSX
4ad28d9a-4bc8-4311-8e74-b7b42f3a308e	Acura	RDX
3cb3d100-5564-4b14-bafa-82b2471c71c0	Acura	RL
edb5dde2-5b40-4629-9861-a06ae9764507	Acura	RLX
154f634c-2e1c-4108-ae98-5e5a400e033b	Acura	RSX
caed5a6a-225f-4688-b755-dbe95e51bd79	Acura	RSX TYPE-S
0b7e9b5a-335a-472b-bab2-32dd1466bcfa	Acura	SLX
8063c984-236f-4d60-81ca-6b9a88ade921	Acura	TL
31a1bd7b-c4ba-434f-b8d4-5310ecafd181	Acura	TLX
5e7d34f0-5651-46d3-8157-0a626914d7c1	Acura	TSX
11e56317-8a6b-476c-9e87-49cfee642170	Acura	ZDX
867f7ce2-189b-4dd5-8c5b-d5296ab864de	Alfa Romeo	145
c91f23e7-5aeb-4cad-a756-fb337fdb9d23	Alfa Romeo	146
cb139a78-4c70-4e61-9cf0-2781e621cf4c	Alfa Romeo	147 3 Doors
c138be5f-c418-4637-ba88-917aa5c6dd14	Alfa Romeo	147 5 Doors
614c8cf0-1d72-4f6d-96ca-8a1ba66fce0e	Alfa Romeo	155
7fcdb112-69c7-41a8-aafd-c87203e57c85	Alfa Romeo	156
5e8dbc27-5976-4a23-aa8e-f87c01e65725	Alfa Romeo	156 Sportwagon
9c34a20b-91cc-45ae-a660-85dd4a3c2585	Alfa Romeo	159
0ab73820-8c37-44e0-ac6c-c3e146e4b4f4	Alfa Romeo	159 Sportwagon
c3785924-ebcb-454e-8f40-8faa42e0fbaf	Alfa Romeo	164
5ec8b134-d4e6-4d67-8878-5cef4a9236c3	Alfa Romeo	166
7c9bd08f-24fc-46de-99e0-d22ea5dccba4	Alfa Romeo	1900
7120b75a-323d-4c75-a74f-17cd8cec5dc5	Alfa Romeo	2000
e6d4d1f5-6b7c-4def-8810-2ed5d8015c9e	Alfa Romeo	24 HP
8adbcba7-c57a-4677-99b1-ee601a08591b	Alfa Romeo	2600
eda494a4-10d9-40c1-9419-7ef990870678	Alfa Romeo	33
2bf8dd38-ca6f-42d3-86d1-55aa51cd5ab2	Alfa Romeo	33 Sport Wagon
2a2e70f9-f3a3-4854-931b-16f1fc440938	Alfa Romeo	4C
ba2e7b22-0c67-4242-816b-b963c4451c34	Alfa Romeo	4C Spider
f84bc024-16a0-48a8-b492-9d2c5a72fa33	Alfa Romeo	6
ce5a04f4-96d8-4f65-9e82-7417e78b7cf7	Alfa Romeo	6C
86c0cbb0-cf40-4507-bdcf-6e096de31dc8	Alfa Romeo	75
074680f2-7bf2-4840-bf8a-09973eb55de2	Alfa Romeo	8C
95d13cb5-dcf2-4dd2-b8a5-1a5e56baebbd	Alfa Romeo	Alfasud
87cac8a8-4619-40f7-8d58-c31fe492400f	Alfa Romeo	Alfasud Giardinetta
abb5958f-b570-434b-8ba6-6bcc1c4ddbc3	Alfa Romeo	Alfasud Sprint
128a99a3-c84d-4e58-b898-16c18e55fee9	Alfa Romeo	Alfasud Ti
74100186-118d-46dc-a1a2-b7b65c2b19f1	Alfa Romeo	Alfetta
e7690583-d502-4834-8dd8-70b9ae00e934	Alfa Romeo	Arna
4733c824-1941-4159-86bf-7442f63a1997	Alfa Romeo	Brera
427ecd5b-536e-4405-bd35-e61a592c7856	Alfa Romeo	Crosswagon Q4
320ebb29-0f80-42cb-a888-319bc633650d	Alfa Romeo	GT
1a22d634-34e1-4faf-b79a-68729346b2c1	Alfa Romeo	GTV
6f8b2db2-f5b9-4c0e-a3ed-6aca8084d382	Alfa Romeo	Giulia
2fc04a40-cbdc-478a-bb60-78d05127ba3e	Alfa Romeo	Giulia GTA
1a42db08-ecd0-4a52-beed-123a97e31f27	Alfa Romeo	Giulia Quadrifoglio
7aa6181b-3dd9-4e35-b9a7-5bb3ab04e2a5	Alfa Romeo	Giulia Veloce
9196b991-06cd-4597-9590-e645e8a621ec	Alfa Romeo	Giulietta
2241947a-3e1e-4e56-81ab-e9b3262dfc9e	Alfa Romeo	Giulietta Quadrifoglio Verde
3691a3d9-4524-4ecc-ab8b-bb56d2582599	Alfa Romeo	Junior
4f20e88f-9c12-4fc8-8623-87790f2d86ec	Alfa Romeo	MiTo
abafd072-e0c9-47fd-aad9-bffef5668daf	Alfa Romeo	MiTo Quadrifoglio Verde
48f6ff6b-6043-4ca9-a0c5-61fc2129dd19	Alfa Romeo	MiTo Veloce
57b3d202-b619-436f-8987-d277842b8d21	Alfa Romeo	Montreal
efb7e314-f6ae-4c03-b7ba-e7d55a27e585	Alfa Romeo	RL
073fd37c-67de-4b88-8238-f5480fdeb7b5	Alfa Romeo	RM
e5a61105-8110-45ab-9f66-591446d7eadd	Alfa Romeo	Spider
4c082929-f67a-44da-81c8-c15a6e55c304	Alfa Romeo	Sprint
c7e4d5a0-e4f5-47df-a51f-269f50166e90	Alfa Romeo	Stelvio
22eaf9fa-aebb-49ed-ae56-ae9d51eef502	Alfa Romeo	Stelvio Quadrifoglio
d4055c08-a3ed-4491-bc99-45cdfd0a390c	Alfa Romeo	Tipo 33
6901d93b-f267-43f5-8c11-240e0c59658b	Alfa Romeo	Tonale
c4d038c6-5b8c-47d3-be4d-2fe9716aca86	Alfa Romeo	Torpedo 20-30 HP
4ece882a-a943-476f-b2b7-3628f3e4593c	Alpine	A106
61e9556d-7016-40e0-b59b-ac6d77ee34e9	Alpine	A108
edfe1dd2-8717-437c-a7f7-5ade32280fba	Alpine	A110
10e9de57-93d0-45e8-b6ee-ce4ba54a82c5	Alpine	A110S
45b6c0ca-ea47-4960-be12-574b27d46a77	Alpine	A290
d4d66634-4a02-4709-94d2-74b6f44ce155	Alpine	A390
1a7ec5b2-97f9-45b9-a6a8-fe811a5090cb	Arcfox	Alpha S
42bccae1-e1a7-4ed4-b4ec-042a9b3b95c7	Arcfox	Alpha T
25736d62-804a-4a5a-a478-eef12692194b	Ariel	Atom
fdc583b5-25e9-4422-b0f4-d6ddd5c176dd	Ariel	Nomad
29e4dd42-2a9f-486f-bfc5-7edcf8e7c9f1	Aro	10 Series
9a082403-8863-40ac-977e-d5dfe7c4b033	Aro	24 Series
c2fb641a-a304-4e69-8e72-50f2c1cc860d	Aro	IMS Series
1bc55d1e-995d-481f-8743-e87a10622093	Aro	M Series
df9debad-1dde-4ea1-ab41-c0166915465d	Artega	GT
b8bc7316-c67d-405d-9e93-8d5a62828669	Artega	Scalo
ca1f68e8-d7b7-44a8-a747-cb8827022eba	Aston Martin	Cygnet
90ce3186-b4a0-4829-a7ff-d110154a7d28	Aston Martin	DB AR1
65901d6b-283a-47d0-af87-a6eb3df93d56	Aston Martin	DB Mark III
e3a4e77b-42ea-4201-9156-2e372ba19a03	Aston Martin	DB11
c8877e06-2d7b-4ecf-b75b-6595f1219b6a	Aston Martin	DB11 Volante
8040fedf-ea3c-4fc0-a443-bad7c11c0c7c	Aston Martin	DB12
165b0f1a-4f2f-498a-8a43-22c13b544191	Aston Martin	DB12 Volante
d50e502c-3eef-4058-a8d7-d061e90f8e80	Aston Martin	DB2
63ba35e0-a09d-4ef9-896f-6d302a444c9c	Aston Martin	DB4
460b98f4-53a6-447b-8817-30dc0495b2f7	Aston Martin	DB5
f64e9dfc-5a34-4fd3-bc00-9c4616420532	Aston Martin	DB6
56ac39ba-ef34-40e8-90fc-697b87a86a67	Aston Martin	DB6 Volante
edc07d27-65cd-43c6-a2c1-3d371e24afec	Aston Martin	DB7
77b2daec-b3d5-41f7-9afe-8bdd2c433a8c	Aston Martin	DB7 Volante
c4a90efd-8dcb-4275-8bb9-806399eacdd3	Aston Martin	DB9
6a885b37-30e8-4a4d-b81c-9dd96f9d67de	Aston Martin	DB9 Volante
97246a06-f3d5-4f98-a740-f36cf5a49ff4	Aston Martin	DBS
4862d8fe-03c7-4a09-9870-cb9b0ba22430	Aston Martin	DBS Volante
d874d966-ec54-4f95-b691-2aa67a788c77	Aston Martin	DBX
d0f8178a-6cf6-49a0-8965-d25989c28097	Aston Martin	Lagonda
7fa8d4ff-6930-46f4-ad41-bb1b0f7b25ed	Aston Martin	One-77
18c57e4b-2562-4d24-a0f6-591ce841f83a	Aston Martin	Rapide
ae5978d9-dae4-4737-8598-e15132be54ee	Aston Martin	Rapide AMR
e1fd74d8-b933-43e1-a7d8-98a3fa9562e9	Aston Martin	Rapide E
fc130132-8388-40bc-9e47-be75dbd341d1	Aston Martin	Rapide S
de31c8ff-c900-4337-b63a-d0643cdbf43b	Aston Martin	V12 Speedster
a699168a-928a-4cbc-af2d-6ff03ed25591	Aston Martin	V12 Vantage
782bc5a6-9674-47e5-a600-fb486a3e0732	Aston Martin	V12 Vantage AMR
941e68ce-c74a-4b0f-9300-af131c738ef8	Aston Martin	V12 Vantage S Roadster
6f5e4f44-a4a6-4fb0-a4ef-b91d139e2dc8	Aston Martin	V12 Zagato
efb96e4b-1124-417c-9d69-ba9581814936	Aston Martin	V8 Vantage
b3b4d75a-f44c-4f37-bbed-a611b0a35a3c	Aston Martin	V8 Vantage S
fbad0c4b-c337-49c9-8194-fe22ba55678b	Aston Martin	V8 Vantage S Roadster
87e14df5-385a-487a-9e62-66590b3e1092	Aston Martin	V8 Volante
73060ebb-68f0-49c4-90f3-a350eb842268	Aston Martin	Valhalla
30426aa9-63f6-4ecf-89dd-438a27cb1be4	Aston Martin	Vanquish
73a5db31-7ee7-48f0-bbc5-10b9f172e8fc	Aston Martin	Vanquish S
3f8185ce-5df9-43f7-b8a5-953759935c56	Aston Martin	Vanquish Volante
10aa6cce-ca26-45ed-a4c4-e7f1755ca0a7	Aston Martin	Vantage GT12
6ef55afa-fbbe-409e-b65e-02cf685a6865	Aston Martin	Vantage GT8
5069e994-1f6e-431f-856b-d5fa0da0b256	Aston Martin	Vantage Roadster
613e3143-6380-4f87-91b0-98179e1e72e2	Aston Martin	Virage
b5b5e3f1-b24c-438a-90d4-3af97df1d8d7	Aston Martin	Virage Volante
3e1290e8-bacb-4e9f-bf93-692180684cb6	Aston Martin	Vulcan
37703a03-cc83-419e-8d8d-b9df76221539	Audi	100 Coupe
2e03982a-8ebf-4ee2-84a8-446cf07d9e41	Audi	100/ 200
ecdd6d7d-92ac-4077-8238-cf7024d119d2	Audi	100/ 200 Avant
c41f749f-33b4-4335-a375-b0d79c9f0753	Audi	80 Avant RS2
28afdc07-657d-4721-a689-7a2891ebed5d	Audi	80 Avant S2
35bec26b-2773-4d26-ba86-18ec1691d3aa	Audi	80 S2
6521202b-d28e-455e-8334-97653e33bcf7	Audi	80/ 90
4bb34795-3896-4a55-9611-3d663dc36865	Audi	80/ 90 Avant
5b333332-e32b-492c-b791-baa29e581478	Audi	90
9a7fd0db-cc71-416a-992f-69f30bccc51d	Audi	A1
cb055aea-9850-425c-9cf1-06923ec2d34e	Audi	A1 Quattro
e22ea8a7-3878-4af1-bd31-1b6bdd503ec7	Audi	A1 Sportback
84622c7c-a235-41f7-9cd2-50c0ca72dd3c	Audi	A2
a684e5fb-5113-4d44-858f-79580916729e	Audi	A3
83dc040a-7483-404c-8fb8-7eacb763f8da	Audi	A3 Cabriolet
262bf607-9c33-40c1-9b0b-5b8474a509f8	Audi	A3 Sedan
fd019168-548f-4188-8dbe-6b8db17df3e7	Audi	A3 Sportback
dadc23c9-1691-474c-b23b-3182f463ed71	Audi	A3 allstreet
a62bea85-44d1-44ef-aedf-6c6994d64de0	Audi	A4
3c9315b7-8a84-4fd3-b80e-3e3c177162e6	Audi	A4 Allroad
e47df675-2239-4f43-bcac-6807efdcf0ca	Audi	A4 Avant
c6bcad58-819b-4d2d-b849-4e698df2aee7	Audi	A4 Cabriolet
fb29d070-59e9-40c4-b43f-3fdc52834dd1	Audi	A4 DTM Edition
a20b7a59-9605-4ed2-b8e2-ca5014d059ad	Audi	A5 Avant
cc05ab03-4aa2-4571-a932-3adb2e12a80c	Audi	A5 Cabriolet
9e2a64e0-56d4-44ab-b97e-7c1a3d86d163	Audi	A5 Coupe
354c4906-aa7f-4d0c-b1c9-c9a3f30debf0	Audi	A5 Sedan
0c932f4a-5417-485b-86d6-11ee5305e5d3	Audi	A5 Sportback
9238d22f-f486-4ca6-8a8d-aa57c6d11609	Audi	A6
8c26a534-462d-44aa-8946-95c46dc777ea	Audi	A6 Avant
e000cefb-5916-4268-8e7c-d33670d219c7	Audi	A6 Sportback
1bcd2f27-b3e3-4c5d-a78b-95370bed7232	Audi	A7
24315e53-a321-4c55-bf9b-b5bb3f46fe1e	Audi	A8
9b6fc22d-4a6b-4962-90b1-1e3cca4b4568	Audi	A8 (D2)
f1cc52b7-1b06-4872-a0c9-dcf18320f995	Audi	Allroad
48aca932-210e-48b2-9744-d1b9c01a68f0	Audi	Cabriolet
1a72a602-6d24-4480-b1f6-31e2b0dd50c0	Audi	Coupe
ef052376-80ea-4136-a2e1-88691b897565	Audi	Coupe S2
1dc7b1c4-f68f-4b6d-8325-031dd7e0076d	Audi	E5 Sportback
69c9bc24-20ce-4d60-9aef-4b89d0bf8751	Audi	Q2
fca6d6a1-8c57-4eac-964b-6e8c0276dd3f	Audi	Q3
632e359d-5b49-4a8c-a400-17053b316c53	Audi	Q3 Sportback
e39372e5-4f61-451d-8952-83b9280372b5	Audi	Q4 Sportback
bfd709da-5244-4c65-92e6-32f04ad3b6eb	Audi	Q4 e-tron
b9579ca9-5ead-433f-b98d-ea0dd294e6fc	Audi	Q5
dea0a75c-2b87-42bc-934a-c2b17034e623	Audi	Q5 Sportback
0636a64e-7dd4-4689-9ba2-d6c500ed2ccb	Audi	Q6
c9aaa0ba-5208-44ef-85dd-3c9c6601b29c	Audi	Q6 Sportback
83f7f22b-3b02-486d-ad13-f755cc268b5a	Audi	Q7
8cc0ee77-d458-4562-b71c-f74d1ba08618	Audi	Q8
a970508a-c699-40d8-b56c-5afc71c53e09	Audi	Q8 Sportback
9c026a96-2a50-4997-b314-e7933f0ff139	Audi	Q8 e-tron
5c6dbb65-94cf-4626-8b4c-fb8acd6470ea	Audi	Quattro
184b1e06-80e4-4e2b-8f1d-eb6fd7ac6a5a	Audi	R8
1ca5b9aa-e7c9-4d59-b0fb-97771a182b2e	Audi	R8 GT
aa265b66-1f0c-45ae-a7c0-664ee6e4c518	Audi	R8 GT Spyder
de386f2f-60f9-4eb3-8d71-cbf51666700b	Audi	R8 Spyder
191893b3-4593-40b7-be3a-58ee53fe053e	Audi	RS 3 Sedan
6854b6ea-abc6-45de-9eeb-5c6e4fa0e827	Audi	RS 3 Sportback
a82e7dcd-7e0b-4c0c-bf4e-daba396dfb58	Audi	RS 4
77cd563c-0302-4d40-88d2-178d2d9a0401	Audi	RS 4 Avant
388e20f0-db30-47bf-9c35-9e142670c4aa	Audi	RS 4 Cabriolet
8d4f00ed-12bc-4a46-b3d3-5fd53dd146cf	Audi	RS 5
6f6525a5-7516-4c49-8ab5-10f6da19ffd8	Audi	RS Q3
8c48a189-4aa6-4667-9a3d-35a91deb3d53	Audi	RS Q8
a5825a47-f7b1-4941-bb9e-a9d3897e8e6b	Audi	RS5 Avant
5198a28b-70f1-44b7-9d15-b49492a19a60	Audi	RS5 Cabriolet
ee235ea7-2d82-468c-82dc-7b9944100fe4	Audi	RS5 Sedan
6628fd88-8eb0-4a7d-907f-aaf988aef8ff	Audi	RS5 Sportback
8a8f3884-873b-4385-bbb2-014b74f0a3d8	Audi	RS6
78350651-ba98-4d11-b990-1014e84c359d	Audi	RS6 Avant
e9d0b1b7-a2eb-4c48-85a7-3755eee0874d	Audi	RS7 Sportback
9ec8cae6-5114-431a-9084-b0b6970b0fd8	Audi	S1
ed474926-d0cb-4b40-a997-0830cf3c3f75	Audi	S1 Sportback
2708d786-6a28-46ec-affc-7a9fcac1d883	Audi	S3
169be296-d349-4754-b7d1-b4868d58e7d7	Audi	S3 Cabriolet
9275bd14-47a8-4864-b018-aa31be467b1b	Audi	S3 Sedan
c285703f-b6d1-421e-9f12-95c04ee962b3	Audi	S3 Sportback
b0abc956-5c2d-4ea5-9911-6800cf1b07b9	Audi	S4
bc9a0c01-a557-438e-8753-7d18c92dac72	Audi	S4 Avant
2856c125-558b-4e12-9029-d26d25f0ffdd	Audi	S4 Cabriolet
5bb1993b-9183-40d7-a33f-d6b3ab5b94f8	Audi	S5
80e08a45-09cb-46eb-9032-4724a95a975b	Audi	S5 Avant
0611d388-c3fb-456d-b558-481bb8cd6203	Audi	S5 Cabriolet
13e974f0-3486-4d75-88e0-45d25d163715	Audi	S5 Sportback
7fc3ecca-d1cb-4b99-ab32-95cbce20ea9f	Audi	S6
fefbbda1-1715-4de6-a71d-68dd4285721e	Audi	S6 Avant
52ebb876-7571-4fdc-94a2-4bb794cd10ee	Audi	S7 Sportback
f58cec64-7536-4652-af9f-72cbbfba2c8a	Audi	S8
545a2b23-5695-47fd-82ae-312eeae226b2	Audi	S8 Plus
c34009de-7454-45ce-a3f3-f97fc30c0591	Audi	SQ2
939a51d6-8024-4ba6-9863-7604dbdf2c85	Audi	SQ5
71897e54-618c-421c-9525-3f9e42dcee68	Audi	SQ5 Sportback
96c3dd23-e867-40ab-acf5-f4c859ce7dfb	Audi	SQ6
a24a73a8-509b-4aaf-82cc-1f92ff03e97d	Audi	SQ6 Sportback
1d57ba56-e436-40e6-8783-738e5c9e4041	Audi	SQ7
00412db1-522a-4d25-a8ae-7003166f4e28	Audi	SQ8
90677611-c49b-4be6-931f-02c05a1db8be	Audi	SQ8 e-tron
3a416321-a5cf-49d0-997b-6f1115201456	Audi	TT Coupe
2833d2ce-57b7-45d3-9fde-23972fffc822	Audi	TT RS Coupe
2b8538ce-ddc0-497c-867e-a44dd75e55a5	Audi	TT RS Roadster
43d954fa-b6a8-4874-82fd-b584d17dd8f0	Audi	TT Roadster
fb04021b-5a00-4e85-b0f6-94bf5a824669	Audi	Typ R
23f4c2b2-0236-44cb-981e-46c45c7a0c19	Audi	V8
7331a7ef-15ee-4237-9471-7db40f5cb94b	Audi	e-tron
7ee1ab73-ac6e-405d-b522-0579422dfba7	Audi	e-tron GT
bcd55303-de8d-48b7-9cd5-6e05a17d90f2	Audi	e-tron Sportback
96aa3f7d-6056-45fe-94c7-6ef48aad6bc1	Aurus	Senat
719aa066-fe48-4cfb-b6b3-b2b3d16291ed	BAIC Motor	BJ60
2d3f2f07-6338-40a0-a6e0-9899cffc3140	BMW	1 Series
8e8e76d6-48cc-4fce-be15-4f9e8182e6d5	BMW	1 Series 3 doors
a38b84c5-330d-4275-af1e-88b31d24001e	BMW	1 Series Cabriolet
41c8c499-8d70-4e3e-b3cf-569b29644968	BMW	1 Series Coupe
176cce77-6adc-4dee-893e-d5f541b37674	BMW	1 Series M Coupe
edf55d55-9055-4873-987e-082310cad9b3	BMW	1 Series Sedan
dad3de65-f66b-4a60-9e53-e64ffaa62a0d	BMW	1500/1600
b019b22a-2158-4ad0-9b76-a0f05ff90f4e	BMW	2 Series Active Tourer
005321a9-7303-4436-b0e4-591842cd28fb	BMW	2 Series Convertible
609a7ae5-99c2-44c1-9a50-e4603653c0bb	BMW	2 Series Coupe
67433eb8-bdf9-429f-a7ad-f29497b588c4	BMW	2 Series Gran Coupe
9e849d05-8556-435f-b9ca-60ba8f0caeb2	BMW	2 Series Gran Tourer
0cdb1ea8-20d5-4aa9-9318-bdaa1dc05b9a	BMW	2000 CS
85dae33a-e621-4522-807b-3f6339e097b1	BMW	2002
49dca3bf-32dd-46dc-b044-19aa44b33849	BMW	2800 CS
0aba0513-cabd-408e-b75a-cbb5d5f99de2	BMW	3 Series Cabriolet
c2aed9b4-989f-45e7-945c-2953486799fc	BMW	3 Series Compact
248b9d1f-e54d-47f7-801e-f0f6c607fcef	BMW	3 Series Coupe
70bde8cb-8a2b-4d85-9242-6b0f58135ed7	BMW	3 Series Gran Turismo
0c5a2647-d25b-4d9b-9198-df7d9dd1ce4c	BMW	3 Series Sedan
b9397d2e-00c3-4dc1-8f6c-60bc987d1c7e	BMW	3 Series Touring
8902b601-bb18-4201-b3be-e9a842fda2f7	BMW	3.0 CSL
e582e98a-ab8e-4340-a2bb-8fbedf9efef2	BMW	3.0 CSi
075a1a5b-9b79-41bd-a5eb-071cff2cc9af	BMW	3/15 PS
a341d88e-848a-4f37-b2a3-472d19011e2d	BMW	3/20 PS
a260e52e-cb4c-4154-ab30-b28146d522b0	BMW	303
2d4062cc-9e7a-4d48-9efc-eec583adb0b1	BMW	3200
12e88f1f-dc6d-422e-804d-adb1e2e351c3	BMW	326
1e03bf86-f0f9-461c-99bc-dc28766e1add	BMW	327
e2c7575a-1309-4189-a78a-d8989111ba88	BMW	328
5095fb3d-a2a7-4857-a31b-92921b916630	BMW	335
b5672854-5836-44bc-b082-8a1115761352	BMW	4 Series Convertible
b9d17f42-86ba-40cd-8503-1156df365bf5	BMW	4 Series Coupe
70d7972c-d0eb-4743-b185-b5cbbc4cf39f	BMW	4 Series Gran Coupe
67e33af0-a859-4545-9f21-a1f54eba22f6	BMW	5 Series Gran Turismo
9ef376d2-18d2-4945-824e-15492c10b3dc	BMW	5 Series Sedan
6045905c-e297-4fdb-b2d6-e56c33098c4b	BMW	5 Series Touring
34a590ad-bde4-41e8-9f4e-92a58050c4ad	BMW	501/502
6e4e852a-23d4-4181-a849-aaf8c5b648f5	BMW	502 Coupe
1728af57-8e73-409b-8e12-417544b658d3	BMW	503
2c71021e-18d2-48d3-8e08-458cd2dc7484	BMW	507
a3967fd6-f8de-4bbc-bce3-bbaf48885a29	BMW	6 Series Convertible
8f61208e-836e-4779-ba23-5e47a78bc3fd	BMW	6 Series Coupe
8f06ecbb-9203-42cb-bfcb-a67535c8a737	BMW	6 Series Gran Coupe
2070d51e-fd5e-49a1-ba03-fa21449b1ea3	BMW	6 Series Gran Turismo
9d356361-7bbd-4fc3-beef-69cc3129f0d1	BMW	7 Series
50cf7df3-0f35-43e1-8187-2995be5cb62a	BMW	8 Series
bfb012e5-a502-462d-89da-17004688bb95	BMW	8 Series Convertible
e068847d-c196-4598-8cb5-ea76396700d4	BMW	8 Series Coupe
f45d87bb-9a11-4a8a-8ac5-4b6b3b7d8271	BMW	8 Series Gran Coupe
1c2fa259-3396-415f-a00f-abe13ca86378	BMW	Isetta
4ca9d621-484f-4727-a0e5-e0aefb19c714	BMW	M1
c0b86673-65a0-460a-a809-bed21ab2ab9b	BMW	M2
f4615522-5fd0-474d-bc97-fb50e1a8d5b4	BMW	M2 CS
8b62d6af-fffd-4b8a-948d-23b80d0b18db	BMW	M3 CS Touring
41eb50e8-a413-450a-a3ea-21c7a72081b7	BMW	M3 Cabriolet
d00d0a9c-54e5-4eb7-bc6d-f92c9bc6561a	BMW	M3 Coupe
74ab1e4a-1a7e-4b8a-aad5-88ce2c6ffaa9	BMW	M3 Sedan
f30fdd75-93c7-41b0-a5a7-aaeca2da0aef	BMW	M3 Touring
801aa197-2a6a-459e-9e97-5517244cadd9	BMW	M4
49702751-3eb0-4c09-8b42-c0daf35ceedf	BMW	M4 CS
f9845738-c044-48e6-bc27-7d267e78edd5	BMW	M4 Convertible
47b93baf-cf4b-4526-8b0d-446fd1673971	BMW	M4 GTS
9a18347b-4149-4d52-aa61-4a0980443368	BMW	M5
cfeab652-79e7-4282-af8b-9575929a19ac	BMW	M5 Touring
01e13046-2d55-48f2-8fbb-a41d55386f9b	BMW	M6 Cabrio
b3d58ede-ab30-4d47-9c45-fca2b0ca9ab3	BMW	M6 Coupe
67eeb9ba-36f1-4f4b-a79e-11fffa0e0dd5	BMW	M6 Gran Coupe
7475f055-8bdf-40a4-96da-fd59b1fc0975	BMW	M8 Convertible
57b7a0fd-1441-44e3-88d4-2d543a0b6585	BMW	M8 Coupe
cf0126c9-e244-4e97-b77f-f5fdd29194db	BMW	M8 Gran Coupe
13686252-78e9-4650-8822-3987f22d2470	BMW	X1
6c246ca9-47c8-44ae-81e7-213e7209b861	BMW	X2
af0f875b-1977-4cf1-b464-ffe5a186fac7	BMW	X3
b6f49ae1-04eb-453f-a5f8-ffa66a25b21c	BMW	X3 M
effc4d07-337e-4d3d-98aa-62c20857f40b	BMW	X4
90268bc0-4262-4500-8073-24602c20b4cf	BMW	X4 M
e4e3ad54-bb95-40e8-8139-c89fde8a4d89	BMW	X5
7a75a367-125f-428d-9a99-e3706f273bbb	BMW	X5 M
e6b69d38-f773-4a6b-b523-7cb05c3a3aa8	BMW	X6
65fa1309-62d5-45cd-993c-f6809b8c333d	BMW	X6 M
d65b91dd-7ef1-441a-ac9f-d8f017fd16a2	BMW	X7
38d9bf97-4ac7-4f67-bc67-cd78b36e73b1	BMW	XM
941ac3c2-3d54-4388-a3cd-dd9752a791c3	BMW	Z1
09365f5a-5188-468e-b0ce-c0860b79b722	BMW	Z3 Coupe
7f2f2130-f3f1-481b-9363-b54c33039f61	BMW	Z3 Roadster
882bbdf0-ee06-405f-b8dd-c74eaa86fce2	BMW	Z4 Coupe
e50d807f-2dc1-4c9b-b37a-11986e6582c9	BMW	Z4 Roadster
52a7bbb2-f112-4610-9368-d1ca4cf1fc54	BMW	Z8
1fb13acd-e344-4008-be80-c0c7aab53295	BMW	i3
89ec57e1-65ec-4d14-b8b7-c5db3c77cccc	BMW	i3 Sedan
e7164c62-9dd6-4d72-ba60-0ccc2239728f	BMW	i4
257a35bb-fbba-4d80-b6aa-61c9a6024958	BMW	i5
7329e8e7-0cf5-4ab0-b43a-9acd8b3d22f7	BMW	i5 Touring
e4c549b2-b56f-40eb-b1f1-7dee0551e521	BMW	i7
851e8e85-3970-4da9-870e-10881287dc07	BMW	i8
d4237c3f-a0f6-49e3-888a-0667328df587	BMW	i8 Roadster
80436417-76ac-4d48-9e90-f424e4a57ce3	BMW	iX
1bd9d2a5-6344-4b86-8458-b106c7959dac	BMW	iX M
98602077-217a-4096-94b4-224733a571c6	BMW	iX1
596fa1ed-9cbd-4622-ae85-3377f0abd5a3	BMW	iX2
3629aa63-bd8c-47b5-ab72-dde878a46087	BMW	iX3
d38a4c27-7929-4c9e-8b6f-474fe3de7135	BYD	ATTO 2
f8476e4e-196e-498a-855b-d0cef0647446	BYD	ATTO 3
6c3afd5f-08d0-4c4d-be55-c719c12cec8f	BYD	DOLPHIN
7b1aae09-52b3-4bf2-90f4-77ca7edc583a	BYD	Sealion 7
88fb61b7-62a2-48a2-8875-92ef29f4e803	Bentley	Arnage
6bdec848-a280-49aa-b25c-840b89eb54d6	Bentley	Azure
a1939c9f-883e-4350-a046-6810d6adf05d	Bentley	Batur Convertible
26bcc75a-3bf7-4f14-9502-cdaf2e0956e6	Bentley	Bentayga
16b36e9c-330a-4416-97d8-ad910adab931	Bentley	Bentayga Speed
bdc5a820-b97b-4e59-9c20-84b35c259457	Bentley	Brooklands
7b20b936-55ff-431c-be35-a61807332551	Bentley	Continental
77516b31-ac2b-47b5-978a-747bda5f67aa	Bentley	Continental Flying Spur
8595ff5d-ae56-434b-a541-ee34f4b7d030	Bentley	Continental GT
63d74d1a-7349-47d8-893b-fc640db80dfc	Bentley	Continental GTC
4cceb97c-90d0-4beb-bf69-057c25d5d0e5	Bentley	Flying Spur
fff9e008-d026-451c-aabd-76c48a73f3c7	Bentley	Mk VI Saloon
2ece2911-ead1-4734-ba30-33069dec2631	Bentley	Mulliner
35ddd32b-1205-4da0-a4c1-9ff2786838b2	Bentley	Mulsanne
5f1768f9-6be7-49ba-94f5-dab31ad2be0e	Bentley	S1
bf531d43-7076-4ebb-abc2-2a2da31a2ba8	Bentley	State Limousine
252730a0-643d-4a0f-ab0b-4b25c8483275	Bentley	T1
5e45370f-fedd-427e-84c0-d2e1ef122130	Bentley	T2
6252ba23-6bc5-4fc5-9915-6993bcb1f4c4	Borgward	BX7
57f7e3b1-2d0e-4f49-910c-b4e9e27792f8	Bristol	400
b69be80f-7f56-4693-b6aa-a9d70fac4c9c	Bristol	402
0b2d2758-8b8d-4a74-add2-0dc42ca28c59	Bristol	403
2bdfaf22-8a0e-4e11-997b-59c6bb1bb1be	Bristol	404
fe2f6f77-7ec0-412d-a9e6-a5dc52aee788	Bristol	405
35e7a838-4c27-4ff4-89e9-4db994172550	Bristol	406
19d5f005-79ee-4b18-a9dc-ed3243300af5	Bristol	407
7209ab4f-58af-4c0b-894f-e97b352d337e	Bristol	408
200b9a4e-4c29-43f4-9017-71930ec84369	Bristol	411
438649e7-7e5b-43cc-840b-2d59a630b010	Bristol	412
92881299-b6af-4928-a9b2-efb3962661df	Bristol	603
e4a44017-0aed-464f-b156-ff7370b44ae4	Bristol	Blenheim
b4e7be81-660a-4687-a434-9246e50e1c1b	Bristol	Bullet
32d5d4ae-f306-480d-af6d-7e867973c4bd	Bristol	Fighter
dc27de15-9d88-4621-a4f5-6cc9f547be45	Bristol	Series 6
8f56b3a4-32bf-4dcf-9bbe-4b809455558b	Bufori	CS8
2703ab1c-3bad-49f1-b604-2f2510bc3675	Bufori	Geneva
657b5ca0-38f8-4935-89f2-cdcc3e834ffa	Bufori	La Joya
8a3905f9-b979-4a24-b3cd-59e5b81e675e	Bugatti	Bolide
e9ab8cf2-6397-4f94-b229-50e9ef1d4bce	Bugatti	Brouillard
451cb6ef-fd13-45cb-a9f7-e4455c0ef8a1	Bugatti	Centodieci
f1168cb8-fdd0-4363-ab08-2ac0b284ae85	Bugatti	Chiron
da87a590-1e2b-4580-98f8-13ee0fe2a67c	Bugatti	Divo
d38ad7f2-367c-4519-9ff6-54475a562059	Bugatti	EB 110
7ca9b7bb-be2f-4ed5-9226-ee79d853dfef	Bugatti	La Voiture Noire
f3966888-986e-419d-bbb3-4cfc301c9687	Bugatti	Mistral
f347cffd-c41d-4234-aa15-4cf37f1cf7ad	Bugatti	Tourbillon
a30f8eba-a527-4fc0-8602-cb7885f85ac2	Bugatti	Type 10
30f27cf2-e60c-448f-800a-fbae81a266d4	Bugatti	Type 101
994c5f42-fac1-4926-9ff7-99c03552106f	Bugatti	Type 18
49f23505-9857-4024-b377-158836641d42	Bugatti	Type 19
b1279a95-6e7f-4ea8-81b7-d3aefa75708b	Bugatti	Type 2
94f45b01-02db-47ad-a709-20be7f66c5a5	Bugatti	Type 23
91ef76d3-3167-4bd8-aeda-45191f2213be	Bugatti	Type 251
24624cdd-8dda-4177-b343-fb94aa905af0	Bugatti	Type 30
f0fba63e-72e8-4830-8461-cc52a7cbc39b	Bugatti	Type 38
5fc3a9dc-b308-4cfa-a4ff-cad4353d4942	Bugatti	Type 40
8e7b7c29-de77-4ec2-86a6-7135844936dc	Bugatti	Type 41
4047377c-3f5e-4f44-8728-a226d22be933	Bugatti	Type 43
10ce0ea3-adaa-49fb-b83d-96f67a7523cf	Bugatti	Type 44
c9f4e39f-2cc2-4027-bf59-c6793f8c34d7	Bugatti	Type 46
8d1e74ed-e978-4bcd-b294-7db32c772dfd	Bugatti	Type 49
68547e48-84bc-4240-bf4e-07ad1a5f8d96	Bugatti	Type 5
f107eaf7-220a-426d-a665-edb6c55348d3	Bugatti	Type 50
8d33b38f-0297-4c87-8f04-49e39cc70896	Bugatti	Type 55
d6f7ef5b-8251-4b08-92e9-de38b49f296c	Bugatti	Type 57
a1a95a43-cbc7-4116-ad85-1be48597a0e7	Bugatti	Type 64
b7965b57-66b6-4468-9366-8dfe9d81f3f8	Bugatti	Veyron
58944429-778b-431a-9068-5d1f75965c41	Buick	Cascada
08bca5ae-9308-40f2-83b4-e655fa5c8dfc	Buick	Century
317f764b-189a-4b88-bdd9-d574338b04fe	Buick	Enclave
726df964-678e-47b0-bbc6-178339e5d4af	Buick	Encore
2c673111-0119-4058-9238-a303223323ce	Buick	Envision
c99d8576-03b7-4629-a49d-55642b4b1606	Buick	Envista
c7f729c5-815e-463f-9cbc-67136b7a6a89	Buick	Gran Sport
dd0e4ca5-e5e2-4919-a8ad-57c9257f703b	Buick	LaCrosse
cc08341f-812a-4c0d-9963-0ee0849b8006	Buick	LeSabre
39f459b9-4bdb-4db7-acd2-68f39854e376	Buick	Lucerne
d654d51c-81e5-4373-a045-bf2fa4aba9c9	Buick	Model 21
43216414-6154-49c3-984b-02eca108377a	Buick	Model 26
5c5522ee-8a82-496b-a27e-0760924c0088	Buick	Model 27
5150a888-9157-4ad9-9753-3ef130e73c88	Buick	Model 32
94b982a9-a3c3-46b4-9b59-c73f90fd6ce9	Buick	Model 33
e8572eba-3044-446e-bf94-76d70b00a047	Buick	Model 38
29c14a51-76e5-4320-883e-17d46d4ef2bb	Buick	Model 39
83e3e4e9-1652-4238-a7e1-35026b81b1b5	Buick	Model 41
7bdede56-6d6e-4567-a5f5-2dddc2b82981	Buick	Model C
78f5fad5-a2f5-4210-8b02-49bd717b16e8	Buick	Park Avenue
2c086b18-6d83-40b9-90b3-675ec6b31866	Buick	Rainier
06817729-52a6-4d76-93eb-ebe52545784f	Buick	Reatta
47581f4c-3be2-4ba5-b3a0-0e9e52af2944	Buick	Regal
a68c3365-9bc0-4b86-9d1d-ec6fe1233503	Buick	Regal Sportback
787290e3-b662-4028-bc44-06d63e59fedf	Buick	Rendezvous
0a5e94a0-b4f3-49cf-b749-418d5bc4a25f	Buick	Riviera
0b9f9518-acd1-4769-8ed5-a4874d7335cb	Buick	Roadmaster
4303af54-d3ae-41b0-9dcb-4cf854901d50	Buick	Skylark
cbf5b66d-1e9f-4dbd-84d7-2432e5f83897	Buick	Super Riviera
233d7420-8130-4698-b518-876b505eed9c	Buick	Terraza
2ea22b30-792a-4557-85e5-84898d16f14f	Buick	Verano
75439aa9-42b0-4b08-896a-da223dc00760	Cadillac	ATS
ecb5d80b-2634-4be0-9c29-45367bd6f1c7	Cadillac	ATS Coupe
1005359d-bc58-4d2f-917e-4b971418abb8	Cadillac	ATS-V
59de77a4-8e6e-46f0-825f-269e79f04ec5	Cadillac	ATS-V Coupe
2de3fc54-fdf1-4df8-aa6b-3338f78b2932	Cadillac	Allante
c152d7d0-94fe-4ac9-9f5b-c1decbc52f82	Cadillac	BLS
4ddd594b-fc2b-4aaf-bcf0-c22cb09e48f8	Cadillac	BLS Wagon
4117f432-3c52-4532-b417-dc92d2f06adb	Cadillac	Brougham
ecea76ae-9f97-4c42-9ec7-d060d52146e1	Cadillac	CT4
1293a836-17df-443f-9bd2-7ac60705c64b	Cadillac	CT4-V
c3a0880f-e364-46b2-ae5c-4077d514c20e	Cadillac	CT5
32c10146-24db-437d-b331-5b883306f053	Cadillac	CT5-V
e2a00fb6-8102-468b-8874-20f85e7423a3	Cadillac	CT6
1df9ebe1-a83d-4999-b898-e272b2480eb9	Cadillac	CTS
34971e8d-e496-4d77-9ab2-02dacb310778	Cadillac	CTS Coupe
82dce356-acc8-4eaa-90f0-ad207110005e	Cadillac	CTS Sport Wagon
d031e0a0-c44c-498a-a4ee-2125ca470fcc	Cadillac	CTS V-Series
b16b7e26-d736-4554-8953-703422c882b0	Cadillac	CTS-V Coupe
2dbf877c-6a84-455c-941b-724c51ba4d72	Cadillac	CTS-V Sport Wagon
35ecf43e-c057-45fe-a8f3-e9cea0bdea05	Cadillac	Catera
6d6b0e10-a5a2-4175-8192-037d8a079003	Cadillac	Celestiq
f1414f6d-565d-4cb5-bf87-57e3b82cf538	Cadillac	DTS
a3d83005-3918-40e6-81b6-e3462bc0510e	Cadillac	DeVille
b162df81-c503-4325-9ac4-d5895c63c9cb	Cadillac	ELR
e8103d67-c210-43d9-8888-01a4ae9557ee	Cadillac	Eldorado
959a4cf9-81a5-4009-8543-631311d940be	Cadillac	Escalade
380735ad-a3c5-491e-ab20-b677505cda9b	Cadillac	Escalade ESV
83ec9352-613b-496a-b596-2329938c005a	Cadillac	Escalade EXT
7fcf387b-4ed0-49d3-b584-a1a6fff6ac10	Cadillac	Escalade IQL
ab0a7146-c723-4800-9550-917cc013dd1e	Cadillac	Lyriq
5bc754a7-aaba-4674-971f-a95655f8d84d	Cadillac	Lyriq-V
df4b81a0-8ee3-47ae-ae6f-0b55f6ac4e68	Cadillac	Optiq
fb6eb62e-7a8a-4887-9d13-3b49ad95e155	Cadillac	Optiq-V
c4d81cba-d8f7-4ddb-8c6d-e19ee0c4991d	Cadillac	Runabout
1237eef3-acf3-4353-be57-9c9315f621bc	Cadillac	SRX
f4541773-78c8-4e3a-89af-5b5b2f5a0c4a	Cadillac	STS
5ee929ba-c848-499c-9c12-f55a67be69e3	Cadillac	Seville
6153388b-67f7-4dab-b17b-8e7677ec8f2a	Cadillac	Vistiq
6adbdccb-02eb-4cf1-921b-8421f08ee5f7	Cadillac	XLR
8e19e6d3-492a-47c4-b21b-d36d535c7816	Cadillac	XLR V-Series
009d58ed-5e6c-49eb-b1e0-3e4704483bed	Cadillac	XT4
85a16a25-e57a-4e89-8ab5-c64446991f27	Cadillac	XT5
cad2dd72-2d41-496e-8e57-fbfe354768ff	Cadillac	XT6
18de5284-e031-4b60-b084-0b34e8f0fe2b	Cadillac	XTS
e3ed9c93-737f-43aa-9b53-f2a00066a008	Caterham	CSR
6b1d8d57-12ae-46bf-bc5f-b18e0d37ab71	Caterham	Classic
bd75956a-db5d-4442-8173-ab786b3e285b	Caterham	Roadsport
f85bdb59-fd50-47b4-ad23-2f6039fb0959	Caterham	Seven 160
9783d689-bd25-47c5-a9ab-8ed0e0dc2065	Caterham	Seven 620
b9d041eb-a1ee-46a5-b87e-243db701eac8	Caterham	Superlight
f0ebc34f-4c80-4bfd-8807-a4e8f5d3ce5c	Caterham	Supersport
60d02508-5391-432a-be62-f068b41f6789	Caterham	The Super Seven 1600
2712b536-e87d-42d3-aa7f-5657f64be0aa	Chery	TIGGO 7
119942d9-0c9a-4bd3-87df-bca59b19ef64	Chery	TIGGO 8
f77c5755-6fee-425a-b6f6-099e7ca0bb55	Chery	TIGGO 9
26ee8f5e-7c9e-4d60-9661-85174e3f1aa2	Chevrolet	Agile
6f81a3af-24d6-48e6-9032-0e597866150b	Chevrolet	Astro
77abd30d-e1ae-4c83-96ae-74d558fe17bb	Chevrolet	Avalanche
d16a8352-8443-4bdf-b504-4da796ef129e	Chevrolet	Aveo / Kalos 3 Doors
37aaed8f-ee78-4213-acec-aa28e90f7ce3	Chevrolet	Aveo / Kalos 5 Doors
5ef3d213-7cf3-484c-bcb8-be9dc23c347e	Chevrolet	Aveo / Kalos Sedan
d589cbfe-79e4-49f3-a428-325120d49da9	Chevrolet	Blazer
14ff4796-3d96-4df8-9837-fa722b9f284e	Chevrolet	Bolt
2866782a-4a5b-4093-a259-164af8fa5507	Chevrolet	Camaro
8a0c3330-8d45-4179-b937-e8c6d7e6597e	Chevrolet	Camaro Convertible
394db1a0-f3c6-4d98-b1a9-a1a8610c56b2	Chevrolet	Captiva
1c32b6ef-0ecc-49d9-9ded-544b55b881e2	Chevrolet	Cavalier
fa75ac56-edb4-4ed5-9e45-8428fef7ee10	Chevrolet	Celta
28b702e1-ef10-4c94-a188-1434627e9449	Chevrolet	Chevelle
f7aba098-943e-4934-b2ad-86cd6759652a	Chevrolet	Classic
60a717eb-920d-41df-922c-003107aab851	Chevrolet	Cobalt Coupe
dc75593c-a552-46ce-9cf3-bf45589c2479	Chevrolet	Cobalt Sedan
572c9999-236e-43a9-9066-bc32a13f239b	Chevrolet	Colorado Double Cab
ef8a99fd-5c01-48e9-ad80-1bb53a35ff8b	Chevrolet	Colorado Extended Cab
12268108-42d2-42d5-b064-0bdb796bd98c	Chevrolet	Colorado Regular Cab
18b5fe79-4add-4c15-9f44-e3b50f6022cc	Chevrolet	Corvette Convertible
fc47d922-7afa-463d-ab27-79c8ce33ab86	Chevrolet	Corvette Coupe
a3d8ef7b-98f5-4b08-ba9d-a0e2f3b7b449	Chevrolet	Corvette E-Ray
36002f12-3ce6-4b28-9167-378560425607	Chevrolet	Corvette Grand Sport
56f97a13-990c-4d0b-a994-5046249a07d7	Chevrolet	Corvette Grand Sport X
5162e9f0-3be1-48c3-8771-5f1bd9aa7790	Chevrolet	Corvette Z06
92c395c9-f129-4a66-80c3-ea7147942c8b	Chevrolet	Corvette Z06 Convertible
0aabe92b-cb59-4c73-8d12-0efadadc99be	Chevrolet	Corvette ZR1
11b6a005-c3fd-4efd-a318-1d76375db399	Chevrolet	Corvette ZR1 Convertible
6bed9470-aeb7-4851-8adc-e3ab05b7af6b	Chevrolet	Cruze
475c46e7-2c20-4fc6-b9b9-fcd5f01aea86	Chevrolet	Cruze - 5 doors
3b0e4bce-3670-4d59-a280-ac2049a2b0be	Chevrolet	Cruze Wagon
9de6df80-de1c-41e1-b54a-d4b33bdc1a38	Chevrolet	Epica
5551545f-bc32-41e4-aa5e-730273c9c44c	Chevrolet	Equinox
0a09681d-609f-4ca0-987a-b3eff4a00d55	Chevrolet	Evanda
28a93c31-7615-4ae0-bfaf-bed13481ecc5	Chevrolet	Express
40d82c3d-566a-4d7f-8c8a-7d2176d20d01	Chevrolet	HHR
6850faa0-2e12-4909-823c-952ab0fecf7e	Chevrolet	Impala
602475e5-4c8e-4d68-be16-907c0c416c49	Chevrolet	Impala Convertible
4f20bc6d-5397-419a-a002-3737f9b68848	Chevrolet	Impala Coupe
509fdb1c-e612-48c9-a28a-0e67737dd5a9	Chevrolet	Malibu
4db92dfa-55db-481e-8ece-9d5372449fd0	Chevrolet	Malibu Maxx
088fba27-9ba6-408b-b269-eebfdbc73122	Chevrolet	Matiz / Spark
92cea49f-a724-4afb-9246-cf3b60f91836	Chevrolet	Monte Carlo
d1acb9c2-72f3-4f86-867c-21ac1148e5c0	Chevrolet	Nomad
28f844e3-757d-46df-9d09-6c286202cdeb	Chevrolet	Nubira / Lacetti
f4996948-ff7d-4908-9dfe-89e9db6ee6c7	Chevrolet	Onix
e087e115-52bb-4f3f-8b22-995517a7dcd3	Chevrolet	Orlando
30b73310-c3b4-47e7-b13d-a072443593e8	Chevrolet	Prisma
839c1f94-0897-4fe2-9348-6fdc09a8232f	Chevrolet	S-10
76be10c5-ee66-4638-b710-1f6bb920ff0d	Chevrolet	SS
fa3f2079-bb58-4da6-8faf-dd86066c1343	Chevrolet	SSR
12006280-1978-4edf-b468-0b89fbb7881b	Chevrolet	Silverado
a8aec208-b856-46a9-81a3-06af74233d74	Chevrolet	Silverado 2500HD
5607d7b3-1831-4cf1-9337-dfd34e2d488b	Chevrolet	Silverado 3500HD
19bd9a3f-d629-473c-9afa-af830b55bcde	Chevrolet	Silverado Hybrid
b836ea6c-85ce-4c73-80c4-f267bd52e363	Chevrolet	Sonic Hatchback
f4e8bff4-0716-4b20-b44b-d2dd683e2100	Chevrolet	Sonic RS
e0d30a1e-be5e-4e22-a857-36ff5037a664	Chevrolet	Sonic Sedan
9167f928-7e84-47db-9110-fa4fa5a02f74	Chevrolet	Spark EV
1ce84b99-f73d-48ce-b546-e026148f57fa	Chevrolet	Spin
d488f1cf-7b23-451d-aef6-6729dbb749c0	Chevrolet	Suburban
f5256dd3-53f1-40b7-b449-36ef076d32d6	Chevrolet	Tacuma
0a06afb8-55b0-486e-be7c-9da43536ae84	Chevrolet	Tahoe
7a12fc3b-3c09-4bc0-a341-de864c8bee60	Chevrolet	Tracker
7abab7f2-e864-4dbf-bac7-a32e68105379	Chevrolet	TrailBlazer
89836850-c39f-40ff-968d-4e40c95f92b2	Chevrolet	Traverse
92e6d944-66be-44f0-a65c-bbf2a3e48229	Chevrolet	Trax
2e5fa8d4-2a79-4556-a92b-628244dde800	Chevrolet	Uplander
8df6b138-3d05-47e0-a5a8-5cf060139e34	Chevrolet	Venture
c20a5674-f283-466f-a5fb-7f59ea89df61	Chevrolet	Volt
95db7667-01ff-4afe-a90e-03486a7ee9a4	Chrysler	200
5de1e661-7b3d-4059-9df7-10f61fbd801d	Chrysler	200 Convertible
97342993-a8f3-4fca-9953-81b5319f011d	Chrysler	300
56695c24-b671-416a-bb2f-817dedfe52e0	Chrysler	300 Sport Coupe
60387eaa-26dc-4827-8306-07e476cb6fb5	Chrysler	300C
8033c5b9-6292-4f6b-aa06-4b103694d977	Chrysler	300C Touring
8daf0787-88b2-4da8-8697-5e59037c861b	Chrysler	300M
3533b0bb-e995-4414-a78b-e5d4bda28b7a	Chrysler	Airflow
dd841899-4cbd-4192-8b0a-1c5e70d12c5e	Chrysler	Aspen
a96b6587-a8ac-4e80-be8d-eb3f0e7c18e3	Chrysler	Concorde
42d07f81-d6d0-448a-8ad8-618e10ab74ef	Chrysler	Crossfire
3edcf691-79b3-4a28-b24f-74e0c4e975da	Chrysler	Crossfire Roadster
5d7067c1-7652-4547-9550-86dd7af86ad3	Chrysler	Daytona
d06c6e41-75e0-41d3-a996-932964acde39	Chrysler	Grand Voyager (AS)
14e591ce-2046-4b4f-98da-b066bc57fef3	Chrysler	Imperial
187383b0-4fb4-4980-8c57-34be86b562f0	Chrysler	Imperial Roadster
40d73ab0-ebe6-4a7f-85e6-3140eca79bc1	Chrysler	LeBaron
9862775a-53e9-4de7-a436-0f645a3bf056	Chrysler	New Yorker
bb7f80a9-0668-4144-86a9-007f5c769160	Chrysler	PT Cruiser
320e4087-e56e-462f-b9e5-74a35f4d3de8	Chrysler	PT Cruiser Convertible
cdb89e2d-41f0-45df-beaa-fe15c5cdf04b	Chrysler	Pacifica
27073d73-d413-4d90-a719-c94ef22e56bc	Chrysler	Saratoga
6e26c12a-765a-4395-a5d0-5ec70d156e26	Chrysler	Sebring Convertible
48c0da90-6b2d-4b68-a638-585301f6ef58	Chrysler	Sebring Coupe
d3846473-6a23-418b-be50-0b0d24d396c2	Chrysler	Sebring Sedan
5e306bde-a9ce-4a98-a238-5c1793ce4870	Chrysler	Six
ce9a1741-e210-4055-ad6b-038e717f7001	Chrysler	Town & Country
c6625cda-dd54-49a7-aa98-a53d68893df1	Chrysler	Voyager
383a841a-2959-4949-8f09-38f275aea6de	Citroën	2CV
701254cc-0d98-4938-ad46-600eeec0555e	Citroën	AMI
2518268c-ef3d-4f1c-8240-671453d858c9	Citroën	AX 3 Doors
582a3d02-81ee-4a4e-837b-43ff9dac5355	Citroën	AX 5 Doors
8375715a-ac19-4d21-b30d-280ee0307270	Citroën	Axel
ede65e0d-d68b-466a-b320-403c95f59e0f	Citroën	BX
7d5c5d30-2588-40d9-b478-4040736f1fa5	Citroën	BX Break
eaac9452-12af-4cdc-9836-439f36bdc683	Citroën	Berlingo
55c77b72-2dbd-4a39-b79c-e88eb0d892c9	Citroën	C-Crosser
b0775533-b102-4dfb-944c-20987b895e84	Citroën	C-Elysee
d7cb6e4a-d7db-472f-90a2-1cd7a9dae148	Citroën	C-Zero
18590288-a810-42d0-912d-c9de147f6ee5	Citroën	C1 3 Doors
8bd7e428-5d7a-4189-9a29-092648b4b5e0	Citroën	C1 5 Doors
c35de814-4fe9-4cab-a286-7a8e78674b5d	Citroën	C2
aa07521f-bd7a-487e-88bd-058dc9b09eea	Citroën	C3
47924b07-5cba-4115-88f4-0a92b835e71f	Citroën	C3 Aircross
3611b601-9504-4c08-b5dd-da03f173edcf	Citroën	C3 Picasso
27fe7c4b-e2da-42ac-b393-eaae13f7ff94	Citroën	C3 Pluriel
adeae374-e848-495a-8603-d52ec13db082	Citroën	C4
12dcdee2-7534-478f-8065-044891cc4109	Citroën	C4 Aircross
7c13c203-6265-4a26-94f0-158281cd32a7	Citroën	C4 Cactus
c0f2c32c-782b-48de-8bd9-786d0486bdcf	Citroën	C4 Coupe
73287e72-4629-4a16-8844-4bc8cd73dd42	Citroën	C4 Hatchback
ffcd3e2f-274d-445d-9a84-8311cb19a8e7	Citroën	C4 Picasso
58c90b9a-8c7f-49ee-8815-dbf0004f40ab	Citroën	C4 Sedan
22931392-35e3-4b25-9dec-c5a566a81f64	Citroën	C5 Aircross
28f0fed9-71be-4997-928f-a9ef1c04a2d8	Citroën	C5 Crosstourer
a17bec5e-21cb-43b5-8626-35feccb2298f	Citroën	C5 Estate
dfd632dc-ec2c-4643-9106-c3d4e19bfbd8	Citroën	C5 Liftback
2793e163-7e8b-4864-b716-81081fd61628	Citroën	C5 Sedan
9d6b0f53-99a2-4a6a-852a-1d6feaaefd35	Citroën	C5 X
7bde5e00-003b-4dc2-9768-2d809b66e52b	Citroën	C6
adf030c9-6503-4127-b693-7b6aaa2f63b0	Citroën	C8
e6a59376-db75-4f5a-821c-8100c90dba6e	Citroën	CX
aabeb382-c925-4341-813d-65edc3427598	Citroën	CX Break
65b03321-58ec-4c57-8a3a-ec527d320652	Citroën	DS
6412334d-87a3-4ed0-9836-144d1876c133	Citroën	DS3
a5af6ff9-1ae5-41b5-b6d9-72c643e15eeb	Citroën	DS4
f49714ae-71f4-4bab-9009-988c17f9e918	Citroën	DS5
0d4bab2b-8598-4858-8747-471fb0b159c1	Citroën	Dyane
9e0a5ff4-023e-4397-9fdc-4647f215bbcc	Citroën	E-Mehari
3e662809-f908-4c74-8fda-58de7cfc33c9	Citroën	Evasion
e6d35963-4885-49b1-9862-5faa16f98302	Citroën	GSA
a8c45459-0ed9-4275-9101-4679234aeb62	Citroën	Grand C4 Picasso
f4e96bcb-6160-4c7b-ba7b-a0561e1280a3	Citroën	LNA
a0ec9ce7-7e87-4932-acca-8c1f8276d967	Citroën	Nemo
0c2c9341-927d-4e64-84d1-2045f559467a	Citroën	SM
32ea719b-2b8f-4691-99d5-ec260787075b	Citroën	Saxo 3 doors
8e2ba87b-7427-4dfb-b6f9-45f7c5cf73ab	Citroën	Saxo 5 doors
2789ca81-05f7-4211-affc-bbef3d68dcc2	Citroën	Traction
9f72d9ae-85a6-489c-9ab5-3e11bcd2310c	Citroën	Visa
a8c21921-4252-45af-95bc-548e9a42704c	Citroën	Visa Decouvrable
fcc0d127-69c6-4e97-8de5-c84733c12dd3	Citroën	XM
959fb1b8-7258-4255-bb03-b7e4f187b39e	Citroën	XM Break
95682871-9324-499e-a760-847a8b0b2ce1	Citroën	Xantia
12474428-0f52-4314-aff1-c1d51aa8f191	Citroën	Xantia Break
16dac865-5117-4201-90cd-874b2930a921	Citroën	Xsara
5bfb222f-a335-4868-9f97-ea570335e3d0	Citroën	Xsara Break
bbc26153-c497-40e6-9ab3-3f7c93a2a192	Citroën	Xsara Coupe
66454152-52f1-47c8-8570-3b64f886ff96	Citroën	Xsara Picasso
cce961a2-d0a7-4ae1-bfbf-57906989decf	Citroën	ZX 3 Doors
ea72ca1e-4490-4e41-9294-068b847057c1	Citroën	ZX 5 Doors
37d570a3-5d9d-4d05-90b9-91d8b342d323	Citroën	ZX Break
0cb635d5-e3c1-4e17-85ce-6a2c7a5e13b9	Cupra	Ateca
1108cffb-47d5-4286-babb-f9dbaffaf41c	Cupra	Born
53e4121c-420d-46ea-81ca-401d1c14249d	Cupra	Formentor
0fea041c-c2b9-4f9b-9e18-d14c6d9cb38c	Cupra	Leon
e10012b6-66d2-449b-bac4-ad68f62a0087	Cupra	Leon Sportstourer
824e00e4-e129-4fdb-9b8c-5969a8721ee6	Cupra	Raval
92ce7a3b-4cd6-48ba-bd04-e03bc726c199	Cupra	Tavascan
89f780b1-fd24-4dfa-9c89-64614d5dbaeb	Cupra	Terramar
76e8cef3-60b1-46bf-976b-0149eb67c517	DR Motor	dr cityCROSS
67b543f3-afe7-43a4-a866-3040b51556b2	DR Motor	dr1
5e567ade-4f87-4af4-8e68-f53f04a5a3ae	DR Motor	dr2
bf8e81b0-4683-4775-af26-05c36ffebb6b	DR Motor	dr5
0d710449-b630-46c3-ba50-80ad79ed730c	DS Automobiles	DS 3
2d85953c-3abf-4ab7-bf0a-e5722fd14531	DS Automobiles	DS 3 Cabrio
cd492f1f-abd5-44cb-bafe-40748d6a0070	DS Automobiles	DS 3 Crossback
12ed66ed-d44e-46bc-aae5-a712cf00dfd1	DS Automobiles	DS 4
f7e8f09b-d3bc-49a7-8c1a-c4bd17239e36	DS Automobiles	DS 5
c17ba7a0-1244-46f2-ac8a-4568a13aaa37	DS Automobiles	DS 5LS
a9bd83fa-cd5e-49e3-8387-04daff05ae9a	DS Automobiles	DS 7
fd801daf-a266-410d-8878-3220314f091a	DS Automobiles	DS 9
3d1cc8a6-3506-4e67-94f3-625f66e0359a	DS Automobiles	DS N8
2dfbae15-85cb-4f86-b19e-6b2eb9012fda	Dacia	1100
e8126329-c5c5-4a2f-8851-58ae32a17ea2	Dacia	1300
6d2d2de1-071c-4298-bea6-9deb3deb6016	Dacia	1300 Break
b2f7f3af-fa74-41be-a615-02f10561d618	Dacia	1310
04f419e6-55a9-4ae2-82af-3ca285e845f3	Dacia	1310 Break
005e7f38-016d-4490-b0cc-c43af6e544c6	Dacia	1310/1410 Sport
52617c38-50b2-4c2e-9c25-b26b02a58c02	Dacia	1320
1708836d-819d-4999-aa78-fabe5d013415	Dacia	1325
f52eb39f-4aea-4584-9dae-d7bc03b901f0	Dacia	500
f0727a6b-0273-4704-8552-f9b56c5358c8	Dacia	Bigster
b1c394ac-8cb8-41bb-9233-2934b19a7759	Dacia	Dokker
7f225516-da0c-4f05-a350-73bc75c8c1bd	Dacia	Dokker Van
e57bd7bc-3405-40a9-8fb3-f379a85a3682	Dacia	Duster
b0630d0e-1e9a-4a9a-9eb1-b925c9a91be5	Dacia	Duster Pick-Up
b0497ab4-2807-4613-9316-10bc394f6644	Dacia	JOGGER
332dae7d-3c27-4157-989d-86b7c61c5541	Dacia	Lodgy
170a7081-06c7-4eb1-b895-332ae7787c3e	Dacia	Logan
2bd13ac0-5d8b-4d7d-b91d-8024d31d0c1a	Dacia	Logan MCV
5ab5383b-9d3e-41fc-bc41-22970722866f	Dacia	Logan MCV Stepway
9e25b021-8e3f-4093-ba61-8fa25128f1a4	Dacia	Logan Pick-Up
fa46d2d8-a463-429f-9526-6f9be56f8ecf	Dacia	Logan Van
29c8e6de-ac5c-4c10-9648-69859c7be53a	Dacia	Nova
3aec272c-c03d-46be-896b-00190de9515a	Dacia	Sandero
20062cc4-603f-424c-8701-89e7818707ac	Dacia	Sandero Stepway
f004601f-20f3-42bc-ae80-1a761af212e6	Dacia	Solenza
46516489-d13b-4862-834e-d1bd8f324158	Dacia	Spring
087f4aa6-8275-4cb5-901f-b213d3d3876c	Dacia	Striker
d4e939f8-1af7-4bf0-91d2-08d5d363d376	Dacia	SuperNova
17245977-227d-4995-892c-14b9e91719cf	Daewoo	Cielo
c603772d-2c64-49ec-99a7-12b6f507cf26	Daewoo	Cielo Hatchback 3 Doors
8a163ee4-282a-43e1-8ed4-a25d3411133f	Daewoo	Cielo Hatchback 5 Doors
5b77d4cf-a317-4b25-bf3c-034d757f45cf	Daewoo	Damas II
4eb31d4e-f32f-477a-b10b-c278fcd49ac3	Daewoo	Espero
a9ddbc7f-0c75-4bb4-af00-f258fde1c6ab	Daewoo	G2X
39d912bb-d6d5-4bb4-bdf8-fb81c4a15d7e	Daewoo	Gentra
e5f3c29c-7f4d-4c14-afe1-67476f848cd8	Daewoo	Lacetti
25cf66c3-0d5c-4e97-b2b5-5b5eb30fb8d6	Daewoo	Lacetti 5 doors
b8a5eed3-6b68-48c4-8a4b-a7440196e9a1	Daewoo	Lacetti Wagon
79c6086c-e7cf-4c4a-8c28-175056a7ee3d	Daewoo	Lanos
564bd020-04ff-403e-95dd-b07435e5da5e	Daewoo	Lanos Hatchback 3 Doors
132b71f4-d5cd-43aa-851c-9c56e2e78495	Daewoo	Lanos Hatchback 5 Doors
5ce43bd4-265e-4615-aac5-1447d6df30e7	Daewoo	Leganza
c70af427-4238-4113-883a-92447d2c21bc	Daewoo	Magnus
d5d7e5f1-a88b-42f4-b262-d57050ef9713	Daewoo	Matiz
f9ddc78e-5cc3-4c50-baea-476e8b15ee3d	Daewoo	Nubira
6d7d146e-cfcb-4619-8f75-2de01633d4c0	Daewoo	Nubira Estate
e0a0a3ef-116e-4750-a903-78843bc4188a	Daewoo	Nubira Hatchback
570db9fa-b6d6-4db3-bdb7-72c1a6fd6b64	Daewoo	Statesman
c5c4f417-1d0e-4da7-9bb1-771c807b2029	Daewoo	Tacuma
6476dde7-f4ae-4ab4-a9ca-07ec8810dd3b	Daewoo	Tico
af26edda-795f-442f-b12d-ecc066f5dbbd	Daewoo	Tosca
40104498-63c2-4aba-8e0d-86e6b2fa3b91	Daewoo	Winstorm
0af304d1-5aa2-44a5-bc58-ad2bf21e73b3	Daihatsu	Altis
3c1c319e-52b0-45b1-b927-70d2bac2258e	Daihatsu	Applause
b977671e-0a4e-4c38-8192-262596c61f27	Daihatsu	Ayla
2238551f-da15-45a2-ac73-b004eff6b7f0	Daihatsu	Copen
fbad74ab-174c-428c-b589-d4964432a98b	Daihatsu	Cuore
a0c8e88d-2c47-4d6d-9556-792c8a56b561	Daihatsu	Feroza Hardtop
3dd25255-7627-44ae-a0bf-094cd1156b5d	Daihatsu	Feroza Softtop
edca57ad-c083-43ec-b02f-8d3d0123f042	Daihatsu	Gran Move
d5a2b829-f04d-406b-a546-180354db0fe4	Daihatsu	Materia
fa001c8d-04ae-463e-8ba2-637127cad63d	Daihatsu	Move
d52ad717-3bc8-4c3c-bd6a-7dc7e48fc99d	Daihatsu	Rocky Hardtop
8a176c08-f53b-4506-83bb-4e9177de4b47	Daihatsu	Rocky Wagon
979f6801-a748-4212-808c-ce68a7ab11cc	Daihatsu	Sirion
b89f4ac1-3b9e-4cc8-a34d-31cc9da09a45	Daihatsu	Sirion 2
de94d277-9949-4eb1-9557-d962b73b4dff	Daihatsu	Terios
75f90e43-df95-452d-a6e9-1d54b847a1d7	Daihatsu	Trevis
2629338e-e3fc-4592-8e68-8b0c5ab108d7	Daihatsu	Valera
b190df71-5b0a-494b-b909-eb8163bebb72	Datsun	GO
d8f35800-371b-48c2-8b82-2a7c74bae5b2	Datsun	GO+
5ef05246-aa4e-41c4-91b2-e714b823d1aa	Datsun	Mi-Do
727a1095-3d44-4e9c-9a2d-ea19de7749cf	Datsun	on-Do
7cb0694e-5218-4382-bf91-3b4abdc37eac	Dodge	Aries Coupe
4b3bdda8-aba2-4fa2-895f-9bcd59857297	Dodge	Avenger
ebc8a6b5-c22e-46bc-beb6-c30b634c6b01	Dodge	Caliber
8f1e294f-fae3-464b-877c-5d604f024706	Dodge	Caliber SRT4
7e5a4f29-1246-43ca-881d-a17a9d25d553	Dodge	Caravan
b1958490-c76f-4bb4-9b9f-30f01c3ea2a4	Dodge	Challenger
d0a5e66b-c143-44bf-b0f2-9cdbb63aa0c8	Dodge	Challenger SRT
853a10f4-43b5-4b05-b7af-b053346d3b99	Dodge	Charger
ae239fa5-0345-4750-99fb-a0f1d763a533	Dodge	Charger SRT
792ce42b-7f69-45c5-9611-d117f33153bb	Dodge	Dakota
29cb9972-5b22-4dd2-89c3-06eb3feabdee	Dodge	Dakota Club Cab
3e17d90f-03f6-455a-b9d7-91d9c36fddd6	Dodge	Dakota Quad Cab
521fb6ab-f185-48f4-b2c1-9d09ed2a1f97	Dodge	Dart
32c13850-8ed7-47db-b861-5e563a6d8c16	Dodge	Durango
250b142d-66e7-4546-8f78-5cd421aa59f4	Dodge	Durango SRT
6deb6076-3be6-406f-ad09-e7ea6b7e18d2	Dodge	Hornet
d2d9e6d6-5226-4cbc-a05a-9bf7882e017b	Dodge	Journey
2cfc483e-042c-4f1d-ad19-2fa6d92debc2	Dodge	Magnum
ea2b0378-6d20-469d-aa94-ae3de379817f	Dodge	Magnum SRT8
ca9efc04-52bf-4bb4-bfbe-ad251ce472ae	Dodge	Neon
b1fbe0d2-efd1-4c79-a8a0-1f54cb9e9545	Dodge	Nitro
fde72974-49c5-4367-9b8c-9599c6f671c0	Dodge	Polara
d8671b33-4b54-4b31-af0b-fae103c6dbba	Dodge	Ram
fb7c4a8d-5ae4-4dd2-90dd-8a74cf991e62	Dodge	Stratus
17aa71f9-64cf-47dc-940d-ee5fc284df0a	Dodge	Stratus Coupe
40725875-b9d9-4228-9c6b-830ea91f5eb8	Dodge	Viper
b70da557-759c-44e0-9cdf-e8ac71f124d0	Donkervoort	D8
51d078b6-e011-4141-a181-633301ad7fe8	Donkervoort	P24
fb71b81b-4c35-4b4c-ad4c-3e6d914be89c	Eagle	Premier
91d296aa-094d-4228-a91a-5979e2a60dee	Eagle	Summit
2e4eb676-5d0b-42a8-95ef-e777eb7861f6	Eagle	Summit Wagon
f2c36461-ea67-45a6-ab80-3f95a54089b5	Eagle	Talon
7cb3f759-96f3-49f9-ad6a-4b03f6f33611	FSO	125
3d6d112b-5faa-4ae0-8594-e4ca125afc77	FSO	Polonez
d4746298-b4df-48a5-bf87-5f96b199c977	FSO	Polonez Atu
172afa40-363f-43e4-971e-9665976e7ec8	FSO	Polonez Caro
70694623-bb31-438d-a812-4280ea99f610	FSO	Polonez Kombi
dd8347ea-97fd-4eac-b3a6-cc06f0310550	FSO	Prima
11ade617-4d1d-44dd-b06d-f2da1679e6e1	Ferrari	125/159 S
6d313848-5bf1-45d5-9d5e-99fffb952fac	Ferrari	12Cilindri
1ab0bc03-9ca7-42be-8357-1bd22663cac5	Ferrari	12Cilindri Spider
c04db75c-9e30-455c-8bfd-f69eabb4fc4a	Ferrari	166 Spider Corsa
c94a453a-16d2-4c93-9c33-fc9b336bded4	Ferrari	166 Sport
9dfeb183-0d33-4d1c-9336-05c70d487f3b	Ferrari	195
b0322548-61c5-4124-bf2f-052cace84fab	Ferrari	212
912005da-cfca-4def-9db5-8b26b305b4af	Ferrari	250
e0b0da88-0244-4644-aa64-03246a6e0f72	Ferrari	275
88f101e4-519a-4676-9a9d-79c289e44975	Ferrari	288 GTO/F40/F50/Enzo/LaFerrari
d4c6562e-3c0e-4ff5-8e69-aa8d9163418b	Ferrari	296 GTB
0330db39-b84b-4799-872d-7df08e9a9d39	Ferrari	296 GTS
3a054aaa-cf5d-4175-8e53-2c3e42c3c6c1	Ferrari	296 Speciale
feb99873-18c1-460e-83b7-8535f8375562	Ferrari	308 GTB/GTS
caad5d9b-33fe-42df-b7ad-0848aecad550	Ferrari	328 GTB/GTS
482dbff7-7044-4702-b2b8-8c32dcd8d52c	Ferrari	348
f25df813-5243-4749-934e-91dc8dbd97d6	Ferrari	348 Spider
3523436b-9f9a-4be9-8dce-055eb84effe1	Ferrari	360 Challenge Stradale
b5b10196-533b-4a23-bfcf-686fd60405da	Ferrari	360 Modena
c4ec4060-06ca-41e3-b564-10e69e23abd7	Ferrari	360 Spider
ec73736f-efdf-4c77-acd7-ede60038f24a	Ferrari	365
d372c9a1-9f5f-4ffd-a911-8b8ded45c1c3	Ferrari	365GT4 BB/ 512BB/ 512 BBi
6f715718-f63c-4df2-8084-33133fb86884	Ferrari	400
89f275da-74a7-4e8e-8315-0e28fc8f04a2	Ferrari	456 GT
f7e9c3c6-86b1-47a2-8eaf-7b512282ff24	Ferrari	458 Italia
66c5e6dd-77e2-4682-9071-912011bb0163	Ferrari	458 Speciale
1c0964c7-3df8-4d98-92f6-e5108fac98bf	Ferrari	458 Speciale A
e00c801b-fd8b-4b8b-b5dc-427bbdc2b28a	Ferrari	458 Spider
7988f479-6d1b-48d1-aafa-0a7028993650	Ferrari	488 GTB
e864219d-770e-4dd4-bf34-22794a7f9bb7	Ferrari	488 Pista
8ced11c2-ed9e-4767-bd72-461be6c7b91d	Ferrari	488 Spider
1da1a448-867a-453c-9e41-25dd3b7404a3	Ferrari	550 Barchetta/575M Superamerica
13faacf1-ef77-4dc9-a1b9-391c2317dfe3	Ferrari	550/575M Maranello
eeb6d24c-3755-48c3-b1a5-81acefaf2408	Ferrari	599
2114f7a7-f19f-48fc-a7c9-af6e66699716	Ferrari	612 Scaglietti
879fc853-335a-4ffc-9a65-6306c6b975e9	Ferrari	812
64020585-8cf4-4983-b6a4-89e5de7e961c	Ferrari	Amalfi
ca7c4fda-fd65-49a2-a164-037df7051bf9	Ferrari	Amalfi Spider
dd2c3e6f-6afe-469b-885b-a16efb7a1893	Ferrari	California T
b30e70ca-fb6d-48ea-abe5-ce47ab2ec4e1	Ferrari	Daytona
11b84944-2597-4ad7-9633-3e07534fef90	Ferrari	Dino
3e538a19-3fa8-4c86-a980-57ab8c578e0f	Ferrari	F12 Berlinetta
c8fd40ed-d713-4680-b776-e094845705e1	Ferrari	F12 TRS
45bc5a70-651e-4752-93c1-44419b71255a	Ferrari	F12tdf
5b370756-9cc6-4d92-9448-75df2b34f445	Ferrari	F149 California
31a4821f-ac0b-4ee1-892e-23612cf5da20	Ferrari	F355
a6e981ea-be87-406c-b6b5-85158f6c785c	Ferrari	F355 Spider
201ac164-2214-46c7-b45d-9a67a5026f9a	Ferrari	F430
0fffc73a-b2a4-441a-9616-a6b503b1b024	Ferrari	F430 Spider
23d5e858-cda7-4cf5-b051-616feef3deeb	Ferrari	F60 America
1cbe6721-c57c-4de8-989c-3b4953521fcf	Ferrari	F8
4b0e019a-9750-4bf3-914f-9e20430d0f98	Ferrari	F80
38e4437e-67b8-41b3-aa8a-6302d81afd21	Ferrari	FF
6128a791-a214-4125-a0c0-8eb00c256c99	Ferrari	FXX
9b892a33-aa04-408e-a235-1b2b9f3c5334	Ferrari	GTC4Lusso
5d4000f9-aab9-4235-908b-75346d75bb9a	Ferrari	GTC4Lusso T
15402fbe-e0c9-48ba-8652-5b0f1654efe6	Ferrari	LaFerrari
e5b90b0f-4176-424a-831b-87cb38dae506	Ferrari	Mondial
47a2e5d1-fbe3-4daf-a8f2-7f0e2b703ce4	Ferrari	Mondial Cabriolet
af530909-af3c-4cd3-a2e0-8fa07dbfad33	Ferrari	Monza
8f59e867-957c-4e37-aebe-60a1f6362941	Ferrari	Portofino
93c19a57-bad5-470f-a92c-e7e4d40dbb97	Ferrari	Purosangue
9ac1973c-1e12-48c9-90b4-faacf5c97a53	Ferrari	Roma
129a818c-8bd6-4e5a-bd21-bd4432cd65ab	Ferrari	Roma Convertible
83c4e82c-c933-41d0-99d2-487c2299e5da	Ferrari	SF90
fce3384b-6671-4be6-9bc3-a363f85d6736	Ferrari	Scuderia Spider
eb168a2e-4ab0-46f4-974a-c167a2258b4f	Ferrari	Testarossa Spider
090b72e5-8125-4cbd-9987-b0ff40c53208	Ferrari	Testarossa/512 TR/512M
abdd1173-3177-439c-97a3-cf1a61d094d4	Fiat	1100 D
ccf0d870-45ba-40e6-9c68-de1103790663	Fiat	1100 D Station Wagon
e070ec49-def5-4fdf-af97-c62a00dade6d	Fiat	1100 E
22bb8a9e-64b9-4bdc-aba1-66aede2e0158	Fiat	1100 S
0f239e72-d098-4696-abf0-2af626e1f86e	Fiat	1100 TV Spider
371cc783-35da-47c3-928e-05a52fb19b5f	Fiat	12 HP
876bd8e2-4f93-4b22-b788-d7f17a995e78	Fiat	12-15 HP Zero
c20c84d7-b083-465d-8d9e-300359eebf6e	Fiat	1200
3548ae55-5dcc-4364-bf04-f2df92968de7	Fiat	1200 Spider
cf4c3642-6b94-4ba7-8ea4-affe7494104e	Fiat	124 Coupe
55c010fc-1207-49cf-b02d-c571c267441e	Fiat	124 Familiale
bc79e303-d9a7-47ad-b901-f701abae6296	Fiat	124 Saloon
1b0694f9-7a7a-4e46-87b0-8dc953a8f9ae	Fiat	124 Spider
b3f802ed-39a8-487e-a8b9-92719dd736fa	Fiat	125
67186ecd-8210-48cd-b235-ff7ed8284e73	Fiat	126
0dd15bfd-aad1-4dd2-83d3-c3135327535e	Fiat	127
7fd8f8b2-4867-4fe0-b7ee-be7dbe220726	Fiat	127 Panorama
688c93f1-4c08-4707-88ef-99c87a718dae	Fiat	128 Coupe
282367ba-e23c-4699-b7ad-3c7682f723ec	Fiat	128 Saloon
37627f47-fbcc-4d44-9269-3cab815b37ae	Fiat	130 Coupe
24d115a3-bba0-4139-9d20-f6b5e230eb1e	Fiat	130 Limousine
acb95560-8922-4466-b7ad-603dbab0c61f	Fiat	131 4 doors
5772adce-d9a9-4f2a-8d86-1161620072c6	Fiat	132
f33b4032-e977-4642-aa57-d9d3ac96f1e7	Fiat	1400
e3e5e3f3-6f9e-4b52-b115-8783959e62b7	Fiat	1400 Cabriolet
f5b5b35c-cf7e-4a80-9882-ce796ac9db8d	Fiat	15-25 HP Brevetti Tipo 2
a9af3213-0b79-45b2-90f0-d54d02ec3613	Fiat	1500
8c04b361-80d2-40a9-9c67-e94288731b1c	Fiat	16-20 HP
94236fca-ad22-4d3b-9d31-bccd70ac39a3	Fiat	1800 / 2100
6fc006a9-4817-422c-bd97-6d4b080e5946	Fiat	2300 Coupe
765bf054-8bbe-4b88-9835-362d2feedada	Fiat	2300 Saloon
34d42ea0-44e7-4855-81c2-309cbc99209a	Fiat	2300 Station Wagon
632260a9-74a2-4276-92a8-8647cc2b4eec	Fiat	2800
a7c53d41-c513-44b3-b04d-902c2036ef75	Fiat	3 1/2 HP
e1e9dcd4-e672-4113-8c71-0d5449a94c87	Fiat	500
94b3021b-8703-40bf-beda-3d9617fdd424	Fiat	500 3+1
559bfc63-2e1c-4eea-9f1b-fba24feb7169	Fiat	500 Abarth
9ef4b172-8261-4076-ae65-40222f387d8c	Fiat	500 K / Giardiniera
1573741f-323a-4b5a-811a-e6974116fcb0	Fiat	500L
4d82dce2-8b59-4f2f-8a83-2841a56e396f	Fiat	500L Living
b57c064c-43e1-4b1f-973d-4bd21aa7bef9	Fiat	500L Trekking
9d209982-0746-4c9e-a0f7-fbae256feeab	Fiat	500L Urban
9f19650c-f4cc-4c00-814c-d491cf26ed16	Fiat	500X
2eebc1f2-f05a-4534-ac93-cb93983b1d2b	Fiat	500e
67cef299-d683-4721-a439-9fc2f8f4bccb	Fiat	501
bda139bf-beda-44af-ad15-b455c371e923	Fiat	501 S
deeeaec1-a2b4-44a1-b3cd-47a3aba8b0d8	Fiat	502
ab59e36f-2939-49f5-a283-edb23abc4490	Fiat	503 Torpedo
5fe062a7-6121-409a-8d0f-405c08c59728	Fiat	505
7788fd3d-b394-4637-83bf-6e745028c940	Fiat	507
811ea7b0-03bd-433a-b9b9-b685cf5f46bd	Fiat	507 Touring
d580902a-a320-460c-947e-6b37543406d4	Fiat	509
2e073aec-f697-4899-9d8b-3ae7b76b0a5f	Fiat	509 S
b912ba3a-9739-4f22-9d30-d7cadf92bfdf	Fiat	514
5f79ed91-932e-4c09-990b-298726ac0cd1	Fiat	514 Spider
dda982df-da0d-4233-b785-d054af07e830	Fiat	519 Berlina
ecb845c4-2e9f-4838-b842-1e4f41e2afb1	Fiat	519 Coupe
34f6fb8c-ae9d-4bca-b924-23fb54123ecb	Fiat	519 S
87634fd7-67f4-410e-aa7d-f0cfcba38128	Fiat	520 Superfiat
abcd47b9-4fe4-418e-939f-f95b1ecc758a	Fiat	521
b4cf9922-3496-42e1-99b6-c0f6a5d78af2	Fiat	522 C
9679c152-064f-4921-9318-d0200081c779	Fiat	524 C
92163078-3c3c-493f-8d5b-db1dcada7160	Fiat	525
793aa98a-36da-4593-84f2-aa3ee65eb04d	Fiat	527
ee8b08ec-950e-42ad-ba81-61839982d714	Fiat	600
10ce7c1f-ea43-4ded-9f6b-d79dad3f1b9e	Fiat	600 D
4b5eba52-bf43-42e5-94fd-d32c2df31f3c	Fiat	600 Multipla
98b0d9cc-1f81-428e-be42-a55e0efd7df2	Fiat	850
a58271d1-d185-4362-a8ec-e7605b9fb97e	Fiat	850 Coupe
70a685a8-d54d-46c4-92af-a1d9c28f9947	Fiat	850 Spider
527b0a29-ae3a-4ae0-adc5-fc8122108ed7	Fiat	8V
fba2940f-ddb4-438b-85e2-62307ea02386	Fiat	Albea / Siena
2914143a-7050-4ea5-861f-b30c953f42c2	Fiat	Argenta
a2e72500-dec6-457d-a3cc-f1ac5dd6d058	Fiat	Argo
c60da884-d06e-432e-99fc-0d41f29e4c2a	Fiat	Barchetta
5994b7c5-8913-45b1-bc1c-2f8d9645b6e9	Fiat	Brava
539ad721-1fe3-45d3-ae96-4a1e35c6b33c	Fiat	Bravo
9e7176db-7cd7-4aa4-8183-ed9d655b77c7	Fiat	Brevetti
5c9cbc44-ea42-490f-aaf8-a2f82fd045a2	Fiat	Campagnola
d6afd9f7-5125-4578-99a3-96c5ea23c160	Fiat	Cinquecento
1e379bae-7fce-40f2-8fde-abea0b4561aa	Fiat	Coupe
bd2c7525-56be-4636-a832-28ba26a8b48a	Fiat	Croma
faf3a575-90bd-49dd-a166-a8b1a5caef22	Fiat	Dino Coupe
899a4a71-4dd6-443c-aa92-0f5c40b822b3	Fiat	Dino Spider
7a235b3e-c039-4ca5-8886-c1c3f3fc52a4	Fiat	Doblo
c920b34d-f000-4be8-aadb-33c7b2cccf02	Fiat	Freemont
97f78f15-1a25-47c7-a0ea-7e8c94f2029d	Fiat	Fullback
6dd4fad9-8e62-44a7-944a-9d75e0926490	Fiat	Grande Panda
71acf863-71cb-4ff4-9bc3-4c6b3a707fcf	Fiat	Grande Punto / Punto Evo 3 Doors
febf2493-1122-4406-92ce-6f861c5e5193	Fiat	Grande Punto / Punto Evo 5 Doors
6adadb37-3109-46da-8534-65a400295ebb	Fiat	Idea
7b41a9b5-6861-47b3-b2c3-ab0c7329550e	Fiat	Linea
7ee45e98-b50f-4432-8780-c586a68d2a50	Fiat	Marea
5717f09c-6e8e-4878-bd2e-6ab4993faf55	Fiat	Marea Weekend
811025b6-8255-4669-94c7-7b90edd7ef23	Fiat	Mille
1e6de4f7-85b5-45ee-92cb-d2bda874d797	Fiat	Multipla
f96d0314-6d89-4eea-b77b-d808e97d458b	Fiat	Palio
185ef84b-fbd7-48c5-a4f9-8fd88ceba4ff	Fiat	Palio Weekend
c85b0f79-19df-4ee4-af3e-04cfceaa6efd	Fiat	Panda
5bc73101-c044-48dd-ae08-8f986e591504	Fiat	Panda 4X4
4bd38468-ab76-4a1f-a962-e6167a14d65a	Fiat	Panda City Cross
fb3c1acc-edfe-4ff7-b33e-302b6d948a2a	Fiat	Panda Cross
81c6b125-6ddf-4606-a82f-43b8821e1f88	Fiat	Punto 3 Doors
f589310a-8133-440c-a8a7-62a6649fd4f6	Fiat	Punto 5 Doors
a13e72d9-5242-4b73-a9aa-bde610fa521d	Fiat	Punto Cabrio
1c98128e-e779-4ec2-ac40-abd1af1fc366	Fiat	Qubo
307babfc-d87f-42a0-bd1c-2f4b4c212503	Fiat	Regata
f68711cf-5d23-4cea-b5ad-5703facf3386	Fiat	Regata Weekend
9ccc135c-c3bd-4462-b6b2-f361809ffacc	Fiat	Ritmo
6aa3817c-be62-430a-8a70-a4024ae236c6	Fiat	Sedici
3627c820-d852-4d3b-92a7-cfe6fff8a924	Fiat	Seicento
7e5aba72-d3ab-4800-80cb-3f8781446a35	Fiat	Stilo 3 Doors
2cf0b306-4bfa-460c-97f2-2559590312f9	Fiat	Stilo 5 Doors
3b75e261-ab3b-45bb-b7ce-0692780d9491	Fiat	Stilo Multi Wagon
9095c2e4-b57a-4d07-b454-1bfcda55ff60	Fiat	Tempra
58c1c8a6-55a2-4e04-b111-113c75a6fcf9	Fiat	Tempra SW
08180b36-314b-4f74-a3a3-cd6bbdf7a2fc	Fiat	Tipo
32c8ab6e-3b3b-49e5-a5ff-b3a6e3c87817	Fiat	Tipo 3 Doors
31f083bf-56bc-4a95-8835-b19647d12467	Fiat	Tipo 5 Doors
721cdd30-ac17-4a88-b7a2-95e6e53e38dd	Fiat	Tipo Cross
7eaba66b-5d5a-4c82-963b-345dbff57fcc	Fiat	Tipo Station Wagon
9298ab1e-25df-4914-8f1b-3432e5be0f5c	Fiat	Topolino
71d671d5-76c1-4ae6-bb6b-63e5db9dcca3	Fiat	Ulysse
4527c555-0177-42eb-8902-e0cd433af6db	Fiat	Uno
e3d72d8d-ab3a-42a3-b603-450df7380aca	Fiat	Uno 3 Doors
e975525b-fabf-441f-a18e-6712794127f9	Fiat	Uno 5 Doors
9619c9d0-4e7a-4b4a-969d-63c8a0c9a285	Fiat	Viaggio
e88d1b4e-9860-4e86-b7c3-96a7a722641e	Fiat	X1/9
fb45cabb-fd7e-42e5-8ba4-48817b517090	Fisker	Karma
2b53d38f-076e-4bba-94d4-77cc2eaa0ae9	Fisker	Ocean
a773d57a-aaaf-42a5-b1a9-ea39a5c81f4c	Ford	Anglia 100E
c01c0a8a-c623-4773-a0be-92578ae4fbe3	Ford	Anglia 105E
a49d005a-f598-4df5-8b72-af05aa5ef94b	Ford	B-Max
e3e346d8-6005-4cc3-9fc7-b2834ca0191d	Ford	Bronco
fac5997a-4367-476c-a25b-dcaeae16722c	Ford	C-Max
75f51f38-2dfa-4a09-9fc0-620dabc01871	Ford	Capri
0cfc1428-99c8-470b-b1c5-8a48db886866	Ford	Capri SUV
5faea52e-bc25-4384-bb5e-23213f39cded	Ford	Consul
f036ecec-0ca1-4846-8aea-94da159ec08c	Ford	Contour/Mondeo
56dda09e-60cb-4682-a6e6-4b013e9423dd	Ford	Corsair
21a104ba-6399-49ac-b950-e8ac77fda406	Ford	Cortina
4ae033e0-70f6-4759-bdad-5621f31c0762	Ford	Cougar
7e501953-81b4-400b-8856-a5c8db74e910	Ford	Crestline
2eb2e01d-8a8d-4de8-9e4c-0ac74464786c	Ford	Crestliner
87b9b6f2-8b03-4595-b298-aeafa5271a05	Ford	Crown Victoria
792a184b-ff9c-42dd-b7e5-a9f2d6ba35f0	Ford	Deluxe
368cd8a8-9ad9-458f-a478-3704138a7588	Ford	E-Tourneo
9b710a58-275a-475a-8323-400e81be21fd	Ford	EcoSport
ce60f5db-7b09-4266-996d-1e284550a2a9	Ford	Econovan
8fbee837-8056-4b9b-a100-c49d31108044	Ford	Edge
903d486c-15f8-431d-8e18-61e571bda180	Ford	Edge ST
0be11d65-00cb-4ad5-a7dd-327cfa1a373f	Ford	Escape
bbf224d5-e01d-432b-859b-e6f1e52e3b9b	Ford	Escort 3 Doors
37b77131-b64f-42cc-8370-8f097832e052	Ford	Escort 4 Doors
117d7ff8-cc62-4363-b2c4-d7d58ce0bf96	Ford	Escort 5 Doors
3b9d2f54-ec38-45cb-bc5e-765c371fbf0b	Ford	Escort Cabrio
63e7c692-2c6e-4f2e-b756-20f5217b2b12	Ford	Escort Clipper
2bdf6a9e-ac0c-415b-a9d4-552389014546	Ford	Escort Wagon
475420e1-133c-40d7-acb3-1ca0a83f6450	Ford	Everest
b3f6d215-c46c-4cb7-b122-f37e64346bc2	Ford	Excursion
f143029f-b9de-4ca7-8897-99ebbdd2e958	Ford	Expedition
bfcbb034-fa14-4f3c-8f7b-d52b60c8269e	Ford	Explorer
b66bbe5b-951c-406a-95ba-caabc62f80c1	Ford	Explorer Electric
03234b44-7473-4870-92ff-807c897fbc1c	Ford	Explorer Sport
5db21323-cf72-4587-9776-4082f6898d70	Ford	Explorer Sport Trac
8282124c-02bc-4016-be80-09d334cd2e6f	Ford	F-150 Raptor
6b62c946-b363-4834-a7c1-8aa04e398e77	Ford	F-150 Regular Cab
8223213c-3799-4767-bcd5-584c005696e1	Ford	F-150 Super Cab
a6674e81-66bd-4b94-a3cf-3cd4b818ec14	Ford	F-150 Super Crew
0cd6e1d0-5a93-4b99-9ef5-b6268fe376c6	Ford	Fiesta 3 Doors
7d385ff3-6b96-48d0-9504-fbffc2b5db09	Ford	Fiesta 5 Doors
2e14966b-2d1a-4deb-a2d4-2a9bf8b436f5	Ford	Fiesta ST
7ea4ed27-b851-46e7-94e8-5546ee3f9c8b	Ford	Fiesta Sedan
aa1d6fa8-edf4-4c20-93fb-9a1f32e5033f	Ford	Figo
8c8cc5cb-a189-4f4d-962d-e1cc485e34d5	Ford	Five Hundred
fcfc39aa-3eea-40eb-a363-9e744f1f483f	Ford	Flex
2890c76a-06ad-4fc3-b532-de9fa36e339f	Ford	Focus 3 Doors
62aee121-8f2a-40fd-a30a-fe53cde4648a	Ford	Focus 5 Doors
9039025e-921b-42bb-85cf-c533d040b7a8	Ford	Focus CC
f0ee1de4-344b-4342-a769-eadece011ad0	Ford	Focus RS
fafb36cf-2209-488a-89b1-eb3b0f91b784	Ford	Focus ST 5 doors
2d23bfd3-d1e9-4ee9-a314-cc765dcb1f10	Ford	Focus ST Wagon
99903070-102c-4384-bab6-82d4e6849f38	Ford	Focus Sedan
838b7499-72f8-4780-bc78-7a5221d56430	Ford	Focus US
1e160f44-103b-4f1c-91f8-a3a584587691	Ford	Focus Wagon
f53e2515-5c87-4ef0-b4d4-6befd83b1a74	Ford	Freestar
25f9c31e-71d6-4432-83d3-6ce280407679	Ford	Freestyle
1d2ec940-de02-4b5f-aec7-435feba28c42	Ford	Fusion European
e3c4911e-9fd4-4627-bf5c-a2f2577ac7f2	Ford	Fusion North American
e3e24e27-cb0e-49c7-b211-54aec7934a4d	Ford	GT
a4a09c9c-3ff4-4147-9ce6-623b6f161a18	Ford	Galaxy
fa1810ef-b0c0-4c4a-8619-7b2c101dc8c0	Ford	Grand C-MAX
d4e806c3-2d62-4b7f-b598-2d8ad8b3ca6f	Ford	Ka
0474a31f-afd6-4db3-b487-1941aa2e5038	Ford	Ka+
6b1da942-5ba8-49ce-9b64-7d5938bc2ac0	Ford	Ka+ Active
a3ef66f7-8445-4eea-bd46-0067ab8aceff	Ford	Kuga
0271ab3d-364c-4664-80bb-b6c1e9d6fbf4	Ford	Maverick
59b70950-9728-45be-8ac1-2fa0c90ead0d	Ford	Maverick LWB
2f956ffb-a231-4511-9dfa-3dc83520ad7d	Ford	Maverick SWB
37edd0af-d34f-416c-b145-33e47777607a	Ford	Model A
968afcff-4087-4251-aa67-b2765fab86e9	Ford	Model T
015c04c4-ac12-42ba-8422-8cb7fae42d32	Ford	Mondeo Hatchback
ddf6e0db-5dc5-4f3d-b68f-ac234ae9c3ae	Ford	Mondeo Sedan
d1698c26-584b-4b92-a8cf-7b0136741fd2	Ford	Mondeo Wagon
8b367ded-0186-4368-9ad9-4ea7551dda35	Ford	Mustang
66ad78d8-330f-457a-9a3f-fdb0010e67bf	Ford	Mustang Convertible
298e1589-9068-4002-80e5-b7ab6b3e5e21	Ford	Mustang MACH-E
66a15d06-5876-4678-86bd-3e136a6f915c	Ford	Mustang Mach 1
4a6f3e43-05a3-4a8d-a0fe-4efc1a9a264a	Ford	Orion
3e0cd57b-e5a1-4e85-b85d-aefc0ea48359	Ford	Pinto
db188c0c-fb9d-49c6-a818-7c24eb52663c	Ford	Probe
5010e4cc-3d78-4d56-831d-9f7279cdee23	Ford	Puma
7e655b57-66cb-41ce-ac80-0bfa36e93f22	Ford	Puma ST
383a7c9d-64ef-488d-be73-d2a43952bd1c	Ford	Ranger Double Cab
352f7bf7-3df9-4f4c-9c19-949506c871c8	Ford	Ranger Regular Cab
eed634e4-9cd6-49a5-8e44-7bc74441de8e	Ford	Ranger Super Cab
e5ef8cb6-eb70-436b-88fd-a99fa7c7597d	Ford	Ranger Tremor
343c8dce-7188-4cc5-9fc6-7f00a1aaf764	Ford	Ranger Wildtrak
7402b7e7-6967-4dbb-b81b-1f18f4d44652	Ford	Raptor Ranger
de02907d-38ce-4921-ade7-8130aa51d3c3	Ford	S-Max
dfc3d38c-d1e0-40e8-9562-07c1bc38f817	Ford	Scorpio Sedan
45f9f3db-7c75-444d-af08-9a750ee2d73d	Ford	Scorpio Wagon
79f31c88-1253-4c94-8637-2c098c22dab4	Ford	Sierra 3 Doors
84f446bb-79c6-4339-92a3-8c247bb67c2b	Ford	Sierra 5 Doors
843c272f-fc0a-4e78-affb-204c2074f767	Ford	Sierra Sedan
c8bb11f4-4a95-4f3b-8809-0479599e2157	Ford	SportKa
1238878b-6141-4caf-96a7-d8d22a24116e	Ford	StreetKa
6e1491c9-993a-4230-9c5c-b617d6a58b19	Ford	Super Duty
af8f8730-2c4f-4f1e-9043-09d0ebcf657e	Ford	Taunus
60008f4c-33ef-4407-a54b-c359169553b7	Ford	Taunus Cabrio
c58359c2-9df2-4177-9a11-c9e3487e7938	Ford	Taurus
419d5102-ceeb-44db-950d-1d4891f06058	Ford	Taurus SHO
0964b0ce-e663-45c7-808f-996f5ea5b13a	Ford	Taurus Wagon
8dcd2999-2536-4858-80f0-7f6b4d8da6cf	Ford	Taurus X
f36d8487-a10e-462e-b898-1746f471b351	Ford	Territory
59656bd2-2859-4f23-a9a7-285d1e44d9a1	Ford	Thunderbird
70568360-4cbf-47e1-ba28-eea621420a8d	Ford	Tourneo
f84e778d-a000-40cc-8747-375bc2e79a24	Ford	Transit Connect Wagon
157f9680-2f36-457d-b687-5804081d2949	Ford	Transit Custom
b77a0396-7f11-413e-ba0d-4877d1860c9b	Ford	Wildtrak
c94dfd91-f08d-41a0-920d-c5fb5b9d3df9	Ford	Windstar
39921f5e-50c5-424e-9348-445aedc56b4e	Ford	Zodiac
19b34ccd-12da-4f08-82a1-530b465164bc	GMC	Acadia
9a70ef27-b972-492d-9aa4-abf3aa04f887	GMC	Canyon Crew Cab
bb6dd06c-7e50-41e5-a864-92f51d51b464	GMC	Canyon Double Cab
865608bf-aba7-46d2-9fd1-cfccfc823bb3	GMC	Canyon Regular Cab
8ff5e303-afa0-4b49-bcc3-a62738ffd0a6	GMC	Envoy
8af6eec4-f6c9-4a2d-86c3-f34db9b21db9	GMC	Envoy XL
b53294e3-a320-4d7e-821f-85100d230d85	GMC	Envoy XUV
c5fda5be-cebd-4956-8b95-e3610fdb6a4a	GMC	Hummer EV
3e2d9644-514b-45f1-82e7-c7a5a1d559b4	GMC	Hummer EV SUV
989fce89-4cdd-4c1c-9c15-cf514a6e7b85	GMC	Jimmy 3 Doors
2cc65d28-2cdc-4bd4-b10e-136c278850a4	GMC	Jimmy 5 Doors
d43fe896-c050-4860-bec0-d3e66ebe5b9e	GMC	Safari
e5f60b93-fb60-4570-9d25-576e25128163	GMC	Savana
9947e572-99e7-4ddb-96eb-98e0946ff3fc	GMC	Sierra
ab8386fc-8139-41a0-9e3a-1454502d7cbc	GMC	Sierra 2500HD
5fff981d-3e7f-4065-a1b5-9c463e840b6a	GMC	Sierra 3500HD
d29ec9a0-15d7-480b-af19-a5e43a46fd60	GMC	Terrain
619d1fe8-e7ed-4f5d-a56e-5c3f64db4467	GMC	Typhoon
cd9feaa6-8a8f-4a2c-b56b-3a3252bd1cdf	GMC	Yukon
dee32461-fa30-4442-8487-14337889e861	GMC	Yukon XL
d03a3067-cb3b-449e-8897-afd94f47e694	Geely	CK
41d90ad6-fbbf-4e49-b3d0-c28d3b5aff3c	Geely	Emgrand EC7
d3e90de8-aa5a-492a-92fb-aed7c36329fb	Geely	Galaxy
0f0afe98-2099-4998-8c2f-f28199c67a5e	Geely	Galaxy M9
e2b6b201-cc7e-4acd-b4ff-2b548575ff08	Geely	LC
a1b2e35f-8312-4cf6-8806-1eb20930174c	Geely	MK
41f7da3a-d23c-4aad-9b6e-9adcc52e6d5b	Genesis	G70
dc421a4b-7e89-4639-83df-1a87ade404c6	Genesis	G70 Shooting Brake
aee99591-d0e1-477d-9d6f-a94f06e38f20	Genesis	G80
766e036e-2a52-4d34-b277-46a7d7e990e4	Genesis	G90
74c049d3-26d4-4965-a9cd-1524b51f3381	Genesis	GV60
253403ac-c7d0-4f47-b036-56a59c74ec78	Genesis	GV70
b40b3d53-57ff-4b63-9d52-030fe0fc1164	Genesis	GV80
d77b9f0e-8106-4011-a55b-549b193e3650	Genesis	GV80 Coupe
009f2fe1-8324-4c2b-8ad1-c483a07a1649	Hindustan	Ambassador
549473e4-684a-4025-9f50-55ce4a179821	Hindustan	Pushpak/Trekker
4b0fe415-e8b0-4a78-85b7-c93b3429d883	Hindustan	RTV/Ranger
da8f0c0c-16b9-4056-9c36-7a4ab6da94d9	Holden	48-215
11f360e0-af18-4cf1-b124-7d1cdacd234d	Holden	Adventra
72dab115-3ccf-4825-9a17-9d1b7b78df07	Holden	Astra 3 Doors
e724cbf2-2b61-41f1-840a-778e9b32c99f	Holden	Astra 5 Doors
68f5b61d-6a06-4991-bdfb-92e6deb14a14	Holden	Astra Caravan
a9eb100c-1106-49c1-8b25-8321d3cbe62b	Holden	Astra TwinTop
8d443f19-0a3d-47c9-bbe3-8c12ab39e076	Holden	Barina 3 Doors
c5298607-7f3d-4fe2-9908-29b8d508f258	Holden	Barina 5 Doors
1de83429-dee3-4564-a03f-670826b14797	Holden	Barina RS
8c806692-0853-4113-b1ce-aa6386bc4bf9	Holden	Barina Sedan
9cffec1d-3b63-46e6-8167-ae6e6c931ef1	Holden	Barina Spark
01f7eb35-82f3-42dc-8997-940260e8e17d	Holden	Caprice/Statesman
3aad9970-6850-41a7-83e8-d340504bcef0	Holden	Captiva
209f4f7d-90e1-4dcc-9cdc-2d1cd86ac093	Holden	Captiva MaXX
cad1e757-4f0d-4dc0-a808-0f780175114a	Holden	Combo
ecadefd1-d7e9-4aed-8a7f-243b4769c145	Holden	Commodore Sedan
01733b1d-6bef-4d65-814d-622fb34267fa	Holden	Commodore Wagon
6455d6cf-f977-4361-863b-1342452b8b80	Holden	Crewman
2d8b782b-2d9c-4fd5-90af-e1e38062dc4a	Holden	Cruze Mk I
60d58cb3-666d-4541-9075-3ed4cea8f634	Holden	Cruze Mk II
7dd4bdf6-75a4-426b-8b85-0098eb26f980	Holden	Drover
b8b94e7a-b302-4844-8a54-3f8dc3c5e85c	Holden	Drover Deluxe
ff30f8d2-f2c7-483e-9030-526145094e0d	Holden	EJ Wagon
fbd17b86-fb3e-40d6-969b-e64e86b45d84	Holden	Epica
9d64dd3d-7018-40c5-b257-d4925db22678	Holden	FE
737587e7-0c03-49e9-8c16-1949860e0041	Holden	FJ
ea1336f8-dd71-4689-9230-2387eb276256	Holden	Frontera MX
fe6f53a9-15c3-47d6-9cf4-1d3668cc3abd	Holden	HJ
804d580b-efd3-47d0-b5c5-2100b83b6239	Holden	HZ
b1f3e675-a778-4e4c-8fc8-6cdf4d7ef1fd	Holden	Jackaroo
07bc6f56-5e4a-4436-b152-c9bd01644942	Holden	Malibu
77d483d6-88d2-4139-9440-ce8364b5d013	Holden	Monaro
7e465656-5b71-431b-bf75-0d308dac275b	Holden	Rodeo
765478ff-0be7-4a82-9d67-a2be3644f17f	Holden	Statesman
53d84119-b3c0-4230-b9e8-f2c5cd3ea0eb	Holden	Tigra
4293041d-f6fa-475b-8d13-e4d6c91486c6	Holden	Trax
c668294a-dadf-46d1-9cdb-f9fceb91919d	Holden	Ute
390827dd-a15b-49e0-810d-7a33fe77f937	Holden	Vectra Liftback
8afb738f-e466-49ec-8bd1-e091d257f42a	Holden	Vectra Sedan
e52919de-c74b-4c1c-857f-51a11a207dc2	Holden	Zafira
66a34282-7f63-47fd-96d2-5165b1c50e3f	Honda	1300 Coupe
0f3a7b7d-3ddc-432b-a458-89fcc1847c5a	Honda	1300 Sedan
4933c1fb-07df-4b55-8c6e-f6ce2f788b94	Honda	Accord
35c1f741-9ac4-4bfd-9fa4-322793a9c0db	Honda	Accord 3 Doors
0eaa8bd4-f9fb-45a3-9112-ec935bef8c43	Honda	Accord Coupe
8500c12f-dd79-493e-aa5a-3cb221b4ad28	Honda	Accord Crosstour
8e14fff1-728a-4bea-b72b-8114540b2e8b	Honda	Accord Euro
8cb1d626-d50d-4c54-b79a-1933c64dacb1	Honda	Accord Tourer
81821c75-a634-4a0b-91a9-a3efdd117352	Honda	Accord Type R
8e8a1a58-3acf-4fc5-8891-91f343c87c4a	Honda	Airwave
662d9d5d-cc82-49fe-9cb1-7a2fdf510291	Honda	Avancier
38a12bcb-2a92-46e4-8ffb-74cf02f07b19	Honda	Beat
f6f9e1f0-574d-4cdd-b514-20304f87e3df	Honda	CR-V
b72df342-60d5-44fd-bf01-d623bc4e1c68	Honda	CR-Z
1adbcee2-c23b-4416-9082-7da137788e15	Honda	CRX Del Sol
6413f643-53eb-427e-bda1-9572e64dfc74	Honda	Civic 3 Doors
aba35f55-2165-4fbc-b94c-4aab796de051	Honda	Civic 5 Doors
f9c2f781-6fb3-4170-8848-ffeeca569d59	Honda	Civic Aero Deck
23bbce36-761f-4ddb-a872-41858e62e93b	Honda	Civic CRX
37396b09-5647-427b-a8d7-52762b094ce2	Honda	Civic Coupe
01cde07a-d311-46a1-b46a-3aeb8f32d162	Honda	Civic Sedan
2094abf1-0148-4391-8874-cda13e567a60	Honda	Civic Sedan US
a5d65c12-2662-4f08-bc47-37735cf247bc	Honda	Civic Shuttle
06cee689-c877-460f-8882-a3ce3e5f90f3	Honda	Civic Si Sedan US
908742a3-5fdd-429e-9048-741662d3bb2b	Honda	Civic Tourer
d08bac37-0a94-4e48-bcd6-41c2f02716f8	Honda	Civic Type-R
63f16188-5c3e-4875-8f2a-0c5774c1db8c	Honda	Clarity
2fc457d1-2e99-46fe-90c4-2dd9d2809f6f	Honda	Concerto
5e7d8c75-17d4-4437-b491-24b46f29299c	Honda	Element
a79e50ea-4526-4e37-b77f-7adb5b2badc3	Honda	FR-V / Edix
ab564054-fe7b-4e06-b716-1a7fd1cb3512	Honda	Fit EV
042faa6c-c2cd-42e4-96e2-21760c25c615	Honda	HR-V 3 Doors
b9280bd1-3d93-49ad-b6f8-f25c71c6d34e	Honda	HR-V 5 Doors
86e3e9e9-48ef-4707-acb7-bde99d82b43e	Honda	Insight
df7ac7fe-a763-4806-a8f7-a6d509b347cd	Honda	Jazz / City
d217c2e6-30b2-4975-bae9-bb2282159698	Honda	Jazz / Fit
cd80ea3f-7679-4b38-b02c-3068ec0d1b86	Honda	L700
82c16c97-90f1-4df9-8222-cb2f9073a56b	Honda	Legend Coupe
088edbfa-1d47-4c1e-9cb3-df09d4a6629d	Honda	Legend Sedan
cf9fc9c2-3ab7-4eb0-ab99-251961f2324b	Honda	Logo / Fit
6f43ef88-f1cc-4e83-90a2-f7818c07dd86	Honda	MDX
ac7bd136-f5cb-42ab-8ae8-e76081379e41	Honda	Mobilio
2ff30723-9615-49ea-9cb7-340d1db16c70	Honda	N360
791ecc6a-9ff7-4eed-9fe8-c700f70ae133	Honda	N600
d360a42e-c5a7-4e12-9b20-1067eb02f250	Honda	NSX
7638bcb6-64f2-4cae-b733-ec7b83b2cd97	Honda	Odyssey
019178e7-c345-4a5c-a575-30bc114e9065	Honda	Passport
3d7a3928-fec0-458b-95c3-b454cc07c42e	Honda	Pilot
63a5656f-b59d-4a3d-a9be-51889a66e967	Honda	Prelude
3e3e8028-9bad-4817-a735-e549f1ebbda2	Honda	Prologue
4d98bd31-0067-44d3-84aa-7004a49c1917	Honda	Ridgeline
c4e8a5d2-d185-44ec-8dd4-68d47da26b64	Honda	S2000
217bfac1-147d-4a2d-aeed-ede476ec5573	Honda	S500
c1b7c7b8-c78e-48b5-a714-a909c728925b	Honda	S600
bcf9e820-3df1-439b-9db2-56444ed4bf5b	Honda	S660
548e947c-58ab-4862-8030-591c867c88b3	Honda	S800
7aec912c-0741-4a3d-9caa-523a9c4decb9	Honda	S800C
a7abb5d8-9b4c-4dd7-bad9-e52256a90d37	Honda	Shuttle
41c11bf4-04ce-4405-b8a3-e018d6682edf	Honda	Stream
ef54f980-2f7b-4464-b2af-c4369588f63b	Honda	That's
81e17d3e-9367-4bde-a4c7-f3a542ff8ef8	Honda	ZR-V
8e9af2c9-b17f-4363-adcf-424b8cb43390	Honda	e
c298244d-3217-4043-ae28-60fdf5c670ba	Honda	e:Ny1
e0138de7-5017-4dbf-8136-ee20b337f850	Hummer	H1 2 Door Hardtop
7d6aa16c-1b7d-46a8-bd19-eeb6f0723c04	Hummer	H1 4 Door Hardtop
fff85060-7909-485a-a950-bc12afdcdd02	Hummer	H1 4 Door Opentop
523409d1-e9f4-42e7-9e05-98001cf52f26	Hummer	H1 4 Door Wagon
7a597451-17b0-4ec7-8f3e-23fd9f5de110	Hummer	H2
46a595c7-43a8-436f-a8b3-d1893ec89b53	Hummer	H2 SUT
fa5fe6aa-9fa5-409f-b9fd-61641c07e8a2	Hummer	H3
3d9d3f92-9c95-4a4c-9236-cd06c99230ce	Hummer	H3 Alpha
6ddd6d68-b2e3-411b-8de5-60419e9aaf59	Hyundai	Accent 3 Doors
c7520580-399b-47e6-9b81-6a537a3ad38f	Hyundai	Accent 5 Doors
3f687879-0336-448d-b6b9-aef342d28aa6	Hyundai	Accent Sedan
a8596b47-e00e-429c-a444-db62443b4294	Hyundai	Atos
ddfe3f9e-de46-4586-b8e4-c54faf15af04	Hyundai	Atos Multi
19b77814-2b20-432b-86e2-793654a6897f	Hyundai	Atos Spirit
78458a1c-a90b-4877-82e8-f9f895f17f45	Hyundai	Bayon
0292eda1-fa43-4538-a149-8ccfc6f30b0b	Hyundai	Coupe / Tiburon
d6387cdd-d10d-4a1a-b9ff-af1aff3d438d	Hyundai	Elantra 5 Doors
11448653-ab21-40a9-b783-617302f3c0c2	Hyundai	Elantra Coupe
684b693c-8152-4c6a-92b5-1aad0f0708f2	Hyundai	Elantra GT
02ae32f9-5425-423a-9547-9028acf689c5	Hyundai	Elantra N
90c3c97d-5335-4875-ad3f-75bd21eee59a	Hyundai	Elantra Sedan
35cdf221-2b35-4d50-a57a-25758e366159	Hyundai	Elantra Touring
7786af76-a675-435e-95fb-b7fabc2c68e7	Hyundai	Equus
d8ac7944-4fc1-4864-afc7-f423f4886e44	Hyundai	Excel 3 Doors
f2d448b7-9a45-4c05-90b8-8dd86c45e17a	Hyundai	Excel 5 Doors
b36d13d2-d4dd-42ad-8343-9fe99d1767d5	Hyundai	Excel Sedan
13bc47b6-8a00-4fd5-b166-09cfda1c356e	Hyundai	Genesis
b696f750-ef9b-4b42-8906-a3b7d60230dd	Hyundai	Genesis Coupe
8f24dc37-b7a4-4975-b65c-42fa53dca7bc	Hyundai	Getz 3 Doors
bc4a86f3-c33e-409e-90a4-f73e4e105b14	Hyundai	Getz 5 Doors
2d2f0321-2426-47a9-bb27-6a700e225d86	Hyundai	Grandeur / Azera
ef4b7f9d-2051-4ff7-9a46-f59963c129c0	Hyundai	Ioniq
3706dce0-83bd-4fcd-9fd4-50f4e3d50b6f	Hyundai	Ioniq 3
db64115f-2db7-4b94-9ea5-b6aa91226837	Hyundai	Ioniq 5
1563cdd1-9259-4741-bacc-587b75098d04	Hyundai	Ioniq 6
31d1f844-d123-4a37-a325-ffe7b1c67b79	Hyundai	Ioniq 9
7ef4c271-d170-4a66-ba24-c3f9682e438d	Hyundai	Kona
b0f9e71e-a5d4-467c-a124-f57e23f3127f	Hyundai	Kona Electric
11da334d-8dd8-4980-ac7a-13df26d3a4e5	Hyundai	Kona N
7f8776a7-2454-4598-aa70-fcf6d876fa7d	Hyundai	Lantra
bac1bc08-4f26-49c9-9215-545fa9f8dd02	Hyundai	Lantra Wagon
6fb92e3a-2ce0-41ac-aa17-f8393298de15	Hyundai	Matrix
cced3734-233b-4aa1-9eeb-799e98a460ee	Hyundai	Mistra
e59a2584-4d2b-4923-bb67-86b27197e0e8	Hyundai	NEXO
bfbe23b2-4e85-478c-9d38-6e2803c39933	Hyundai	Palisade
28974b35-91d8-4307-9de7-1aec1b9f94c3	Hyundai	Pony 3 Doors
a77a40cc-41aa-4598-886b-61494dcd911b	Hyundai	Pony 5 Doors
c06a82c7-ebc1-4e8e-815f-82dfca3d34e8	Hyundai	Santa Cruz
37d83108-66aa-4585-becc-e2ea74f372e1	Hyundai	Santa Fe
9062cf9f-e5c2-4565-823a-fc50850a2c2a	Hyundai	Satellite
95df45c0-abc6-4d9e-a3d6-570c88bfb2ab	Hyundai	Scoupe
d3707eab-8fb2-4b0d-a46d-284c99847d35	Hyundai	Sonata
2bddf699-3045-4250-8158-427c12475608	Hyundai	Sonata Hybrid
824a27a0-1b47-4b44-a683-2a9a26320f10	Hyundai	Staria
718b81c0-c56b-46db-b708-27224fb5d7d7	Hyundai	Terracan
b3adadbb-c0ea-412f-a6f2-c60ad17daba0	Hyundai	Trajet
30ff30f4-c241-4548-a485-3f7b75c02796	Hyundai	Veloster
4528f7d3-54dc-49e4-82ad-765a0b430e06	Hyundai	Venue
bb0dc99e-18ec-4545-948f-e21086f38fd8	Hyundai	XG
c6c75472-76d8-4351-80bb-0e1aecf50eb6	Hyundai	i10
e03af35b-0a15-4d05-b29e-1e632d4a0009	Hyundai	i20
d675338f-df1a-4359-9b5c-2c463c6a9d05	Hyundai	i20 Active
c8ea2627-024b-430d-a079-59e3054f1f79	Hyundai	i20 Coupe
3a1b60c3-f1bd-4e0e-9bda-0cf92854ce5d	Hyundai	i20 N
2acda785-9039-4a4b-8769-e1c17a50faae	Hyundai	i30
fc2c6741-069a-4883-aa81-ad64ac33dd15	Hyundai	i30 Coupe
7a1b5c5c-e9bc-441d-b4d5-baacbfb9dc83	Hyundai	i30 Estate
d9024dd3-5628-47e7-8f8c-b2556f183a38	Hyundai	i30 Fastback
fee51864-2f9e-4dfb-aafe-e529aec1572f	Hyundai	i30 N
19036ebc-c69f-42f8-8870-d591d1c677d4	Hyundai	i40
8463d976-723b-47bc-95e2-a0b135b8bcfb	Hyundai	i40 wagon
dde030aa-b1b0-4366-b1e1-69c429981ce6	Hyundai	i800 / H-1
1bec4eca-fe1e-4ea9-9e10-ddd78bf0cc6b	Hyundai	ix20
96fde575-3df7-4f12-8614-1f857b797af0	Hyundai	ix35 / Tucson
3a7581d7-1527-4432-956c-e60a99650a94	Hyundai	ix55 / Veracruz
b92ff243-8bb4-4ffb-a0e1-7f616ef8fa1a	Ineos	Grenadier
fab38d48-92f0-49d7-9420-b3c3139fa397	Infiniti	EX
80bfc9c9-0704-4a2f-b6c1-d5a7f68b4e7a	Infiniti	FX
94ccdf02-2c18-40a6-85f3-05d92d0352fb	Infiniti	G Convertible
1c0ba1d5-be6b-4918-ae3c-c5e1f1a19067	Infiniti	G Coupe
763db419-fcdc-4245-8421-7f755a46a097	Infiniti	G Sedan
844145b0-7714-4d60-b200-a237d1a0d24a	Infiniti	G20
dd50b52b-5764-4332-b819-d4724fe4881b	Infiniti	G35
852d8931-1209-4391-a2cd-3de20056f93d	Infiniti	G35 Coupe
8ff5b4e1-da93-42d3-b10a-28dd08167605	Infiniti	I
67e6fa13-18fd-418d-839f-80e2a27cf77b	Infiniti	J
265582b8-fb1b-4919-9d7f-40bbd456d522	Infiniti	JX
498a04a8-fdba-49a6-99c7-df4fdd122fb9	Infiniti	M Convertible
cb8b4d0c-8e2e-4dee-a6c2-460e9d5e3415	Infiniti	M Coupe
77301558-1a7b-4bd4-9f68-104b81625992	Infiniti	M Sedan
0537bc0d-a525-4f7e-a5c6-fa8b920b2230	Infiniti	Q
9345c613-073d-4c08-a592-8d8b36a80dd5	Infiniti	Q40
ec0ec60a-732d-47c5-ae6b-762a5053d2d3	Infiniti	Q50
70bf6bb0-a2fa-4ec0-a479-30c216b357d1	Infiniti	Q60
18f768be-f13c-444e-b274-86ed9773d9ed	Infiniti	Q60 Convertible
98aeba3f-5490-4702-b3d3-cd76413f2763	Infiniti	Q70
e96e0de2-3b7f-45f7-af25-32dfa563deac	Infiniti	QX
71e9fff3-d410-434c-88fa-9b8918cf7b50	Infiniti	QX30
52dd1eb4-d3f7-43f6-9c80-0a560ea60d73	Infiniti	QX50
b37fe0e0-42a0-449c-a5cf-6935ca7b4008	Infiniti	QX55
fab99bd6-5348-40e9-8401-69f3b27a1fdd	Infiniti	QX60
b4ec0605-024d-4b2b-bbbd-d27e6a253e4e	Infiniti	QX65
bf1fa73b-6331-4b23-9518-d62168079d08	Infiniti	QX70
c85c82f0-b0fb-46c3-b503-e2f9b318702d	Infiniti	QX80
b0f552f6-2f88-49d8-b9de-56a0365d82fa	Isuzu	117 Coupe
26d5a7b8-2784-4824-8855-790aa68f8403	Isuzu	Amigo 3 Doors
6686ff86-9109-423f-b3cb-e22b3b21ecee	Isuzu	Amigo Cabrio
7dff7393-3c0d-4f3d-8dd2-21d955e22505	Isuzu	Ascender
ef6e6132-ad55-441c-9d51-a35e0d880d6e	Isuzu	Axiom
3aedfb1f-b47b-4105-9793-6681cc571200	Isuzu	Hombre Crew Cab
e7806249-c68a-4ab6-8e3b-50d3640b863e	Isuzu	MU-7
41cef06b-9857-486e-97cb-faabcc929b95	Isuzu	Rodeo / D-Max
abcf38c2-3c26-44bc-b913-1d18e996d8c8	Isuzu	Trooper 3 Doors
e50f0be0-28b4-4587-ad65-111e5f9c366a	Isuzu	Trooper 5 Doors
6e72fce8-d135-4638-9863-f6ff7df744a4	Isuzu	i-Series
dfc42dd9-61b7-4c13-90ae-45edb681a8ec	Jaguar	E-Pace
de259015-3576-4379-b05f-9c9875280d1e	Jaguar	F-Pace
5ed747bc-5ae1-4471-a409-3c6070f04b9f	Jaguar	F-Pace SVR
5a18c793-0343-4bd8-af55-123d34742ffc	Jaguar	F-Type Convertible
66c3ae61-86b0-4df0-90d2-9bfff7ec6624	Jaguar	F-Type Coupe
680b5f44-faaa-4050-86ce-7e9fc3e1a5e6	Jaguar	F-Type SVR
684df572-d390-41cd-98d5-b2346f9f1803	Jaguar	F-Type SVR Coupe
fdbf2f11-bb4e-4637-aab8-bfa1adf715ef	Jaguar	I-Pace
065e8979-4dd5-4b0a-a28f-ad7bd44b1e63	Jaguar	S-Type
fa55cb81-4637-4878-a931-4c3573f1886d	Jaguar	S-Type R
97e9e940-9a07-4bd6-8f66-5226085d5030	Jaguar	X-Type
eb2e40b2-cd3a-40d4-8c01-cd14f87cb1f3	Jaguar	X-Type Estate
2a37d205-3ba1-4985-8ff8-b1966a4e52fc	Jaguar	XE
d56844e3-9dc9-4a7c-8d62-6c86a38436f1	Jaguar	XE SV
0d9f1f71-1b1f-4e35-988c-46a48d3ac735	Jaguar	XF
766b7665-37d7-4b34-ac3e-8d7654d740e0	Jaguar	XF Sportbrake
fb481315-9faf-43c0-91e1-c434fb582dce	Jaguar	XFR
0934fd8f-77ed-40e4-a06f-31285efd123d	Jaguar	XJ
de5fb3eb-2f55-4c3a-94d1-1c1db46ed18f	Jaguar	XJ220
54d37242-4d1b-4c74-b50f-681e68fdf472	Jaguar	XJR
f13b6fb2-018f-425b-a0e4-cc253f9ca0fc	Jaguar	XK
fe0145f9-8698-4bac-8404-e4ae05a23e9a	Jaguar	XK Convertible
e93ab8bc-9e83-4a16-9ebf-319f0b20605d	Jaguar	XKR
67b0fbd8-2be3-460c-b48a-9169d67a6f39	Jaguar	XKR Convertible
e7286c9d-c369-40f4-b580-80fa4f40d302	Jeep	Avenger
1b67a949-9a09-4aa1-86d1-54d61c41f244	Jeep	Cherokee
02a37313-669a-439b-aa9f-50aa4cf3c705	Jeep	Cherokee/Liberty
bc0782f2-7de9-4197-9970-bb4018a6cce1	Jeep	Commander
4e9fdc87-f36a-4c89-87cb-7d3ed5fb83ab	Jeep	Compass
b05e1e1a-8863-425b-9238-75892991c2f1	Jeep	Gladiator
df4560ba-ad03-483e-ae1d-341605d44768	Jeep	Grand Cherokee
781ebcbd-7427-4c5c-b31b-aec9dc8d045a	Jeep	Patriot
b132c6f4-e35e-410f-a76e-8bf0a482374e	Jeep	Recon
85a4c4f2-9c2b-43f2-a285-66515d8eeab9	Jeep	Renegade
a37efc60-68f4-415b-8d36-68a40b19ae82	Jeep	Wagoneer
8aeff134-fe88-4888-8e1a-7c2d747fdc78	Jeep	Wrangler
93f7dca9-8912-4d00-bf75-20e0c85b4978	Jeep	Wrangler Rubicon
ee92d06f-5159-4136-b317-b19ed24fa629	Jeep	Wrangler Unlimited
66459457-5569-44f2-a04d-f532c500b098	KTM	X-Bow
bc24bdfb-0cc1-4e64-814e-d6ae7a6e0894	Kia	Borrego / Mohave
0bfdec5d-0427-4753-acc4-02b547541f5e	Kia	Carens
04d8bfc1-d163-406c-bcf8-87ba3c213389	Kia	Carnival / Sedona
1db89fe5-544d-4381-bb58-269ef4bd5cd6	Kia	Ceed
53523ed3-4248-4024-af06-dbd402914e42	Kia	Cerato / Spectra
52d7f39a-539b-4d65-847a-225390b8aeef	Kia	Cerato/Forte
d7ae61aa-d31a-4cde-ad6c-ab71eaf54338	Kia	EV2
8cb17956-189c-4598-b074-cdf3f82f6e34	Kia	EV3
3597befe-a1eb-4d44-bb6d-c90457e2a5ac	Kia	EV4
3e2ab636-376f-4207-962e-60102dbe9e28	Kia	EV4 Hatchback
f9bd677e-20d9-4894-a649-7eba13efd53c	Kia	EV5
d62c581c-4259-4c87-83dd-991127f1cfe4	Kia	EV6
a0222f50-7746-40da-9815-a0d920807acd	Kia	EV9
6e34a104-9862-4a0c-9ea6-43c6177bc332	Kia	Forte
21dd423b-f9ec-44b6-8a45-0fd0e9fa3988	Kia	Forte 5 Door
136a7111-9eab-4477-930a-85c54b5c5bcc	Kia	Forte Koup
d5d64650-02af-4c03-b73a-98ad6080062d	Kia	Joice
6863f66e-4d2e-478f-9d02-37d5e5f5ec3d	Kia	K4
4264b50c-e82c-458c-9880-09f1df60d86c	Kia	K4 Hatchback
686012db-c391-470f-ab9d-22fb6a5f346f	Kia	K5
5de6147a-e586-4c8c-b6d0-6c55cb53fbf2	Kia	K7 / Cadenza
6c639c5e-d8a4-480b-9b47-a4af35b029ee	Kia	K9
8a5d8287-00c8-4e3f-a717-dadf4a5f3c4a	Kia	Niro
3d4f4eb5-a23c-4933-bb42-066f66be2651	Kia	Opirus / Amanti
f93b8d8a-1002-4711-bba0-16da4ed6bd4e	Kia	Optima / Magentis
621a05e4-db59-4092-87a5-7c4d9dd8a075	Kia	Optima Sportswagon
b1eaa267-ba02-4722-af28-aace77156d70	Kia	Picanto
f7fec1f8-9d0d-4a64-aea9-b68deb53af12	Kia	Picanto X-Line
ce9cf8c3-ca7a-4791-8385-e7d29d15a5b7	Kia	Pro cee'd
c029df28-f5b1-4e2a-96a8-b698fd71cff3	Kia	Rio Hatchback
f21b473c-20f0-4443-9a9a-6def7084b4e6	Kia	Rio Sedan
becc935b-b72a-4f8b-ac02-7bc8a794facd	Kia	Rondo
7f584fb3-dd5b-4a73-8b0d-b634c172a692	Kia	Seltos
eb1f8183-9018-4b2a-a14d-c2f3bb04ce14	Kia	Shuma
4270fb74-3374-471a-a6a8-82828970fe3e	Kia	Sorento
6a227e4f-96c0-447a-98ae-474d368ac2f8	Kia	Soul
3a084590-df10-40cb-a95a-a7e92aba35c8	Kia	Sportage
514fcbc5-c582-4940-a321-ddad599588b7	Kia	Stinger
cb740529-053d-4e39-b555-8176c5b0e97d	Kia	Stonic
7ba30f24-ef5c-4531-a2dc-581cecdd5da7	Kia	Stonic GT-Line
b64df310-99ac-4cb8-8fc0-31685c079f7b	Kia	Syros
b8a87d70-094a-45e6-ada2-dedeffc289e5	Kia	Tasman
3ad06309-c259-4450-82e0-0cedf085737b	Kia	Telluride
e2cc4d70-3ef7-4946-8482-3e2895afbcfc	Kia	Venga
f3f0fb62-fd12-4281-84de-67dabe2dcd24	Kia	XCeed
eadb510c-f315-472b-9b39-1ea7daed9482	Kia	cee'd
14b68090-c789-4c89-8c74-5bff7999dfa9	Kia	cee'd SW
c8d399b2-1aa5-4ee6-895f-38573406819f	Kia	pro_cee’d GT
c9724190-ad33-4c02-9aba-bef1b2b80dc7	Koenigsegg	Agera
ed75d753-12d6-459e-b1cd-505f89c376ca	Koenigsegg	CC8S
bcad7e58-0aa9-40a9-a922-22c9e6cbb781	Koenigsegg	CCR
f11ac8b1-1995-460a-8dab-678bf4bab0d2	Koenigsegg	CCX
89c73aaa-126f-411a-96e1-f4d564e9ee3b	Koenigsegg	CCXR
b8315d4f-8baa-469a-ac39-772f0ae3221b	Koenigsegg	Gemera
4ff0e692-cc73-4dbc-8a87-7c3bf18b16d4	Koenigsegg	Jesko
e54d569d-fa22-4525-8118-8fa50bb51c90	Koenigsegg	One:1
c4b752d4-303d-44cc-a27b-6e56e713f1c8	Koenigsegg	Regera
7df9fb85-57d6-4908-ad60-612f2b05aa6a	Koenigsegg	Sadairs Spear
6c79db1b-6290-4033-a084-5161eedcd240	Lada	110
de29b6ad-67a2-4e28-b23c-ad6b7e31e154	Lada	111
022491c9-f15c-47db-8e6a-a3e10adc7f53	Lada	112
692d332d-2e05-4ba5-816a-b41ab8e1d5b4	Lada	Kalina Hatchback
7c29fab6-c107-4802-9b24-2bdd5a8570e9	Lada	Kalina Sedan
ebb7cd2b-d801-4f9d-a9ab-0c54979f48c0	Lada	Niva
f53615c4-ec9b-48a0-a3e8-c53950dcdf9f	Lada	Nova
b2ad3c96-bdd7-4e65-8e2e-00ee1708a14f	Lada	Nova Combi
3d0fe4b1-5674-4028-a424-fe9e2d001d0c	Lada	Priora
4d8cc429-3526-4d40-b04f-a79b60dfe0a8	Lada	Samara 3 Doors
adf1d302-c966-4cf5-b7c5-c2b4f87730a7	Lada	Samara 5 Doors
7c351095-8e3a-45fe-8e0c-20c8200c57ee	Lada	Samara Sedan
e52cc9f3-591d-437f-b8ab-53a6bf855eb8	Lamborghini	350 GT/ 400 GT
12c2d5a2-22f4-44c7-a7a4-b307c772f372	Lamborghini	350 GTS
94f342c1-d624-4a01-991c-035bc80d5ee4	Lamborghini	Aventador
f55513c6-fa76-4056-81cf-e1bec6604568	Lamborghini	Aventador Roadster
3879502b-5ac0-414d-94ad-e6dd1d00ffd3	Lamborghini	Centenario
771b1ecc-f2b4-4373-a812-6dc9967cbe54	Lamborghini	Centenario Roadster
3f50884a-4e69-483c-8d96-98d39188ec0e	Lamborghini	Countach
cb03e588-ee13-4b33-918a-7d56b26565cd	Lamborghini	Diablo
5d8d15d2-9bb6-42ca-b8b8-2033f9e20cc8	Lamborghini	Diablo Roadster
f4d1c705-8a73-4085-9bc7-369df8b63506	Lamborghini	Espada
71a04501-9edd-4eb8-8bef-9c82cd420d4f	Lamborghini	Gallardo
22bf181a-0b5b-462e-8d1f-32fa6a515949	Lamborghini	Gallardo Spyder
50085e73-c944-4a4c-9029-d9454a513467	Lamborghini	Huracan
973f9988-01ca-4e15-999c-8a9ba7b418d0	Lamborghini	Huracan Spyder
76e7ef8e-5b0f-451a-83cd-d2d94511b7da	Lamborghini	Islero
255bbe42-2b6d-42ff-811b-087fa289562f	Lamborghini	Jalpa
86dabbd6-66af-4661-9ad6-733094a12f40	Lamborghini	Jarama
8eefcbad-de5a-492a-a58c-5e68b4d8e935	Lamborghini	LM 002
fec343a7-8139-4d8f-b2ba-3a2dcd910bd4	Lamborghini	Miura
2eea8baf-908d-4562-b06c-b64fb9a0a729	Lamborghini	Murcielago
06d8948d-5792-4eda-9102-65a31c920942	Lamborghini	Murcielago Roadster
0bf26299-386e-497a-9f73-6e91da117084	Lamborghini	Reventon
41cf0c92-0445-46ed-bb54-ed98fc88d6d1	Lamborghini	Revuelto
96738c97-790c-4b8e-a148-67b58bde10d4	Lamborghini	Sian
82644aaa-a57a-402c-a3d2-bd8f4ef6635c	Lamborghini	Sian Roadster
3318bf92-914b-4deb-ae6e-b5dcef03261a	Lamborghini	Silhouette
7b90378a-c6a9-4a76-921c-180a83a3f47e	Lamborghini	Temerario
c270c793-ea34-437f-9363-f5989d5efc5c	Lamborghini	Urraco
89b0343b-8546-4676-a2ad-26c0b1bd60c0	Lamborghini	Urus
b55f094d-2a8d-40e4-9bb2-ba1deb32adbf	Lamborghini	Veneno
d4b3553f-22ae-42e5-87fe-9e3f79a76b23	Lamborghini	Veneno Roadster
4283eee0-4da3-4ee1-8c71-be874bb5b83a	Lancia	2000 Coupe
2ee7e45d-d450-4ed6-ab1d-990d9460228d	Lancia	A112
4d829b7a-f8d0-4fbf-9e0e-67d797e551b2	Lancia	A112 Abarth
027dbda2-7e6c-4b33-9044-2aab9e32f34f	Lancia	Alpha
62ec871b-c5d3-4fbe-baaf-192be0734cc9	Lancia	Aprilia
4b8d52a8-1f93-4148-a126-966a55232ae3	Lancia	Ardea
692cf610-f565-42b3-ba99-cb7345f2a1a2	Lancia	Artena
bffb7cd8-34db-4150-a1b7-c298f3691e0d	Lancia	Astura
6bddd96e-655a-4418-a7b2-0d8e46053698	Lancia	Augusta
c247870a-9a67-44cc-9678-23b00cf8ba8c	Lancia	Beta
5e1de5b9-8c9f-4b87-909f-b44d84ecb126	Lancia	Beta Coupe
16bcee59-216f-42b8-a3fc-6adf9b927c43	Lancia	Beta Montecarlo
cadf7d9b-bf4c-4b4c-b282-8075941d56c7	Lancia	Beta Spider
ee06f1f2-f416-41e6-9e17-50ffe420a084	Lancia	Dedra
3cbe09c3-62e1-4466-90a4-a9d3721dde7b	Lancia	Dedra Station Wagon
1f5f96ec-f1a8-41be-9b0e-ee8f4fa3bb06	Lancia	Delta
1ca5bf6c-fbb0-4144-8a2f-0b2b70a66a95	Lancia	Delta HPE
b214e9ad-c111-4560-b3c1-ea7f676f5adc	Lancia	Dilambda
dfde7752-d1e8-4355-8b89-19341e3cd854	Lancia	Flaminia Coupe
d1932321-d95d-4c45-8dc9-2bede245c3cc	Lancia	Flaminia Sedan
cd7bc1be-0fa9-4bce-8da8-7d0b2e6ea9e7	Lancia	Flavia Convertible
36deb21f-615b-40b5-a0a4-d675342b2a29	Lancia	Flavia Sedan
8311d378-7fc9-4ed5-bcda-ae5b214e2603	Lancia	Fulvia Berlina
55e3b429-5808-4960-aaa4-179c9c959dc0	Lancia	Fulvia Coupe
fed3d9b9-afa1-4c74-8091-09e8f87d7972	Lancia	Gamma
2fd32ed4-5c6b-49d5-bfaa-3859007cd643	Lancia	Gamma Coupe
29c49e84-9a7e-4811-8de7-cf8d847c3892	Lancia	Kappa
f70c1b87-009b-4797-85d5-a77e016356a4	Lancia	Kappa Coupe
9d32d1cd-76cd-4c01-ad5f-b5856be343b7	Lancia	Kappa SW
f608815d-b13c-473a-9dc4-819aab79e8e1	Lancia	Lambda
871f5c31-303e-4af3-aea9-63b338925025	Lancia	Lybra
12d5e8db-ac90-4cca-bc17-5e8759e4e395	Lancia	Lybra SW
d98679b4-fc9e-443a-86e0-37341509d9da	Lancia	Musa
27d4c835-e424-4c17-a5a7-13e31142e1f6	Lancia	Phedra
4e4c63eb-3d44-4dfb-be46-2c3957725b2d	Lancia	Prisma
ab38463d-8303-4d17-8b93-906be844a920	Lancia	Stratos
1dbdcc10-2c6d-4a11-b028-b42829addcf9	Lancia	Thema
e7677e1c-af12-48c0-8193-80d4ce9c18ad	Lancia	Thesis
29dd5105-f36d-4505-ad54-cd43d99164b1	Lancia	Theta
6dd6f9c0-0b37-46c1-b456-b9e8748eda23	Lancia	Trevi
462ff434-d039-4da7-a764-30fb0500a53a	Lancia	Voyager
8a2edb93-a232-466c-8db3-839d7a831e65	Lancia	Ypsilon
e52f9e44-c231-4d59-b2d5-c2b93d5a4000	Lancia	Zeta
1740f369-62ea-4edd-a0a6-57d7b7003a7c	Land Rover	Defender 110
ebffc508-bcd1-4792-8877-c2f246272d0a	Land Rover	Defender 130
0bf9e613-9a0b-4cb5-8942-912354eb93ad	Land Rover	Defender 90
245420dc-eec1-432e-a989-97c5963e84f2	Land Rover	Defender Octa
d00a5e63-0b7d-4c97-9dc4-0dbcd0b09486	Land Rover	Discovery
be2d1dcb-7412-4c4b-8b08-82db7f189c64	Land Rover	Discovery SVX
dcbbbf0a-84f4-4bd2-9aba-7cd8b9ed2bb8	Land Rover	Discovery Sport
08f53eba-26ea-407f-ba10-a2bc83a96ada	Land Rover	Freelander
c453322d-70ed-4e9f-a194-211bc1ced853	Land Rover	Range Rover
1f6a2aae-db55-44f9-ab2b-45556e2c56aa	Land Rover	Range Rover 3 Doors
664e09e4-2516-49cc-bc19-6bfec2dc09ec	Land Rover	Range Rover Evoque
7c5aa1ec-afce-479f-84ba-2aded7532039	Land Rover	Range Rover Evoque Convertible
7cf4e465-f969-461c-ac99-63a06e4361f2	Land Rover	Range Rover L
9d508038-b58a-4ecd-9f24-03015834664d	Land Rover	Range Rover PHEV
d3f46c92-7902-4353-b519-58761d68a5c3	Land Rover	Range Rover Sport
fdb1a22e-5a44-4478-afe7-8a310e081f51	Land Rover	Range Rover Sport PHEV
140cdc3d-353c-440f-8bf8-ab3ba5dfc24e	Land Rover	Range Rover Sport SVR
bed348a6-d3f4-440f-9fd1-8a6d6943d25e	Land Rover	Range Rover Velar
9667b03a-b362-48a1-a93c-d476a336045b	Lexus	CT
4c9cf845-a616-43f4-b165-ff29106d0f42	Lexus	ES
f0d24272-b91d-47ef-ad4d-d4bc90fc0d38	Lexus	GS
d018d6d2-06b0-4d1e-b3da-19ca65db4033	Lexus	GS F
0ae9c3f5-ac08-4f5e-b174-59eb934b6740	Lexus	GX
1c4f71c2-d5b0-4650-b6f9-b89612ad5fe7	Lexus	HS
d772a455-a6d0-4b0c-8c5a-43562401b4c9	Lexus	IS
99a6527e-a1dd-41a6-a2b0-e9d66323912d	Lexus	LBX
d564427d-15ec-4bbc-91b9-ebf54c4b3d89	Lexus	LC
1edb6dff-aa4d-486a-a227-6ab16ed5ad03	Lexus	LFA
7b9c38d6-1bd6-464b-b9ab-ab38a263f989	Lexus	LM
58e227ca-db42-42fc-b0b3-66ba38d582aa	Lexus	LS
3f4aea94-c9dc-48b5-b361-65ac8bdb857e	Lexus	LX
b7b9c315-92cd-42fc-a650-e8b5df43f324	Lexus	NX
65a86022-0669-4ab3-a7da-c67d055f7138	Lexus	RC
4de7d451-876d-4fc8-b08c-f3583bbbccdb	Lexus	RC F
36079bb0-8f5e-4a89-b6b5-2b48a9225e03	Lexus	RX
e94a7861-5a72-4be6-9673-4d003c61f689	Lexus	RZ
dc2a3386-6b85-4896-893b-139819a745a5	Lexus	SC
91fae182-8e96-47aa-aed4-318d466f2444	Lexus	TX
4215bcce-f3c3-4b1a-a1d5-98c8d4b14713	Lexus	UX
d67d40cf-8336-4397-a889-70d9e5422da5	Lightyear	Lightyear 0
08769b3d-ae96-4e84-ba9d-6bdd2ef292fa	Lightyear	Lightyear 2
6289d724-9c20-46be-922a-2357918bcaa4	Lincoln	Aviator
def5bab6-d92d-48fc-8795-00aeccfe5f5b	Lincoln	Continental
efef323e-22a4-4c78-a199-609819d26e76	Lincoln	Corsair
e46b1ad0-9318-43d5-a00e-a007f87d47bf	Lincoln	LS
c52a7587-07fb-4239-9f79-081831b0372c	Lincoln	MKC
dc2b84df-9214-42ae-b026-747c0423f211	Lincoln	MKS
6278117d-90fb-4b68-9a60-08c754a67d36	Lincoln	MKT
261609ee-6be1-4ad9-86a8-3a3e16746ea6	Lincoln	MKX
b7441750-9aee-4101-a5da-2bd1cf4136e5	Lincoln	MKZ
dde83a22-27e0-4d0d-a830-249bbe7cf34c	Lincoln	Mark LT
94562e85-74e6-40c6-bda5-bbbaf578cf06	Lincoln	Nautilus
60733fa5-1b6b-42b6-bce5-ce5f8e1535a1	Lincoln	Navigator
7b937900-a569-4d31-a5ab-8cd555b20715	Lincoln	Navigator L
1c2bf7d5-536a-4f40-b067-cea0f1f3adab	Lincoln	Town Car
20a499c4-ada7-47fe-8098-410213ae7786	Lincoln	Zephyr
c612c73e-a6da-481f-a5b5-065e7cc70545	Lincoln	Zephyr Fastback
0f7a616c-89ad-48a9-bf52-4d2bfc491cac	Lotus	2 Eleven
6523bb11-caa8-4b38-b9a7-acc5bab07c0a	Lotus	3 Eleven
07420920-018d-4b84-8242-b41edf16e635	Lotus	Elan
d39def06-7efe-4ec3-97d2-b3ee5c90396e	Lotus	Eletre
b072736a-9f4c-4107-86a8-17a1804dd091	Lotus	Elise
36e41150-d46b-4cab-bbfd-8d6e1deea602	Lotus	Elite
f1467d7f-4824-4aa0-8d89-32f0103f4c01	Lotus	Emeya
c53e2442-cc95-4f05-b175-3d296dededc3	Lotus	Emira
fd37f8c2-6175-49d0-98ef-a61ca6ec7b63	Lotus	Esprit
19b60710-b99e-4487-8ce8-2762db28f2c7	Lotus	Europa
60d276da-2db6-495b-96c7-7bbd6bcb74a7	Lotus	Evija
cd663cc6-a04f-40e3-b990-c348ddf09a92	Lotus	Evora
94bd39a4-9ad0-46b0-b085-5446fdb9dbd2	Lotus	Excel
f21c10b8-78e3-4260-a633-1638fc3f3e61	Lotus	Exige
7be7e21a-cb2e-46f8-93c2-b468729e76f6	MG	F/ TF
c02fd966-d5fe-4196-9040-eb04abf6b848	MG	GS
0829143f-19e8-4187-8513-0ec59a83a3b5	MG	HS
fca20b7f-cc32-42c0-bfcb-349455cb44d5	MG	IM5
aa8d27e7-61ae-4f35-bda5-de42dc00afd4	MG	IM6
18c7d237-c09f-4728-88f3-319b74e8442d	MG	MG 3
555f7b88-885a-42c7-bc46-f626c8cd696d	MG	MG 4
4edde516-f118-4edb-ae40-2007e624d7de	MG	MG 5
76778761-1933-41d5-87c1-e1665f34dc3b	MG	MG 6
4111bc71-41fb-4752-aa1c-5ed3bcfe897c	MG	Marvel R
3a88c8f7-54ef-456e-92dd-a18dbcad1d2c	MG	S5 EV
f88db678-9799-4a87-a55d-1e3604124b52	MG	U9
d62f4557-1549-4cce-98f4-c920e4bf6d52	MG	XPower
d7e4cafe-540d-4d9c-9adb-7aaf16c71aea	MG	ZR 3 Doors
878f944e-a6b4-4158-b8bb-7c234a511e64	MG	ZR 5 Doors
e7bdb93f-e600-49ae-9cfe-cdfb1e32a48b	MG	ZS
1bf59115-25cf-4110-a937-ddf859656895	MG	ZS Hatchback
16c4e4ec-7729-467a-ba40-2cbca7cb7394	MG	ZS Sedan
38c55943-543f-441d-a6b5-1ad61b105961	MG	ZT
d0c95c71-7e6c-4c09-9ab7-dc58cfab20ca	MG	ZT-T
c7d1e8dd-17ae-4240-91b5-4a9413688ff6	MINI	Aceman
f947d637-c475-4243-ab76-6774f4642b76	MINI	Classic
d12c23dd-f2fa-4e8e-9948-991d9ab710c7	MINI	Clubman
b51999db-c2ae-4ffd-87d0-fbd9c55204a0	MINI	Clubvan
ae76d091-e5fe-4852-b130-608f4d9e396d	MINI	Convertible
a672a476-b891-474a-a3fa-798ff0948f43	MINI	Countryman
de817493-2158-4f01-b7a4-3ec337c433d4	MINI	Coupe
e18566d5-444d-4f47-ae4a-7ee3f9c22602	MINI	Hatch
c6dc4948-abc2-476f-8fa9-fcfad38bc6a9	MINI	Paceman
4f7e859c-b8d2-4ec6-bd33-406dee8d40e8	MINI	Roadster
6552d742-55b3-4054-9108-40c23993c17d	Marussia	B1
1215a550-0de3-4230-94d9-47cba9687600	Marussia	B2
835dd052-0743-4f30-b91b-59ba453b1852	Maruti Suzuki	800
5ca1b821-b71a-4f37-9bda-10e286deb0b6	Maruti Suzuki	Alto
081f6fe2-87f1-48ec-8728-f85db4882e00	Maruti Suzuki	Baleno
005e3c26-76cf-4146-96e1-502caa304eb9	Maruti Suzuki	Ciaz
bb831eb8-9041-413e-aa6c-0759b7fbc22a	Maruti Suzuki	Esteem
96a62845-7efc-419c-8f69-fa7994af2c94	Maruti Suzuki	Gipsy
01143dd4-b69c-483c-a794-a4cde5827add	Maruti Suzuki	Swift
261e8b34-1471-4576-a678-b38f161a2d58	Maruti Suzuki	Wagon R
441a2a3c-386d-4a02-b63b-81accf2f7fd8	Maruti Suzuki	Zen
bbe426a1-03ce-4924-aae0-a3eea92c0854	Maserati	Bora
f99ad24f-a5fa-4e6a-bfd0-25a7f5e47e2c	Maserati	Coupe
2cd6a769-5b66-4e28-8531-df7b7aaef775	Maserati	GT2 Stradale
b06682bf-e987-41e7-ae80-2d0b38686440	Maserati	Ghibli
09f6bc37-50ae-47f4-b6b4-e2922532acee	Maserati	GranCabrio
485577a7-7bbf-4a3d-af67-b2f796c47c11	Maserati	GranSport
a142d09a-7bcc-433a-930c-005ae9b2cd29	Maserati	GranTurismo
41534e90-51b9-46f7-828e-c93c4c2c69fd	Maserati	Grecale
5df1d685-d79e-4fcb-b31c-f9bf83020e52	Maserati	Grecale Trofeo
c1de5bc2-045a-492d-83bf-cf5487fb5482	Maserati	Indy
bfd619f6-ea7a-4d3e-ba00-653089e3bf7c	Maserati	Levante
06ac2dd6-721b-44ca-ae12-7e3690530c0f	Maserati	MC 12
78c3674b-d84f-443c-9aaf-a6128244973b	Maserati	MC20
162a0482-9a24-4185-864a-839883fcddfd	Maserati	MCPura
59e8c687-e7db-4ad1-9ca5-5912b41bb888	Maserati	MCPura Cielo
cd687384-4eb0-4876-bb30-a4e8fb82f04a	Maserati	Merak
f478512f-959b-4144-93ed-88fe29472336	Maserati	Quattroporte
dcd313a9-e108-4e38-a12f-2ead1f55e25d	Maserati	Quattroporte Sport GT S
04935a42-080d-4436-b2af-295fc8c05866	Maserati	Spyder
44a06f62-d6bd-4433-a95e-fbffd2a4efe8	Maybach	57
9f5ab459-c53f-48f7-947d-5a2dd9c400d1	Maybach	62
9422cd18-a2a9-4de4-8f63-f91c993db0a1	Maybach	Landaulet
382936aa-8a65-467e-b926-d6fb4acbd420	Maybach	Typ 12
e38d0abd-ac4c-4594-a4f0-74dbb5fc04a4	Maybach	Typ SW 35, SW 38 and SW 42
d1b89b02-2342-46dc-809b-3042cfbb86ac	Maybach	Typ W1, W2, W3, W5 and W5 SG
c36870cd-18a4-440e-87f7-6d819b361754	Maybach	Typ W6, W6 DSG and DSH
6414a9dc-c04a-4f10-a529-ba0159458afd	Maybach	Typ Zeppelin
7491bfa1-25e0-43ab-8c45-3b24a7bf11cf	Mazda	121
64b5dd60-9db9-4612-b887-65a9db03233f	Mazda	2 / Demio
02e05747-1a95-4bfd-bf4a-0701dee0321f	Mazda	2 / Demio - 3 doors
06ed574a-bbab-4187-a024-78716f569a86	Mazda	2 / Demio - Sedan
ee90d9ec-08d9-4a08-b260-377e0b74bb66	Mazda	3 / Axela Hatchback
a2a19bfb-fecc-46c6-b02c-a79547923d9d	Mazda	3 / Axela Sedan
a9fdd51d-2cf9-4594-9cc1-d65f275d7d9b	Mazda	3 MPS / MAZDASPEED 3
4138152c-0524-4a69-9382-af532e839cf5	Mazda	323 Hatchback
b940f200-d6d7-4def-a5f2-4b31a0e52559	Mazda	323 Sedan
707ca812-946f-4eef-b6b0-3e22c554b6b9	Mazda	323 Station Wagon
37d032f3-6eb1-41a3-a588-8d5f0a48cc86	Mazda	5 / Premacy
6d4380a4-6833-4bcb-9e1f-f203a41ae770	Mazda	6 / Atenza Hatchback
67de482e-a0c0-41fb-b71d-95fb3ebe6e6c	Mazda	6 / Atenza Sedan
a0e96074-c9c4-408b-82ad-edaf701f11d5	Mazda	6 / Atenza Wagon
4e5e30c3-abf1-45c4-9a95-546c1f4c537c	Mazda	626 Hatchback
23e7dadc-bcfd-4986-beb5-555c52434b88	Mazda	626 Sedan
57e9a0e9-6aac-4b57-a0ed-72586c7c0ce8	Mazda	626 Station Wagon
6cc02a8e-d081-4efd-9788-99ac20ce152f	Mazda	6e
135a955d-56d8-4962-b2be-7c7bbe146e36	Mazda	B Series / Bravo Dual Cab
814305ea-71e6-46b9-8de7-2d5982a4cf48	Mazda	B Series / Bravo Freestyle Cab
beabb004-d961-4255-85b4-8bbd2fa3f3b7	Mazda	BT-50
f8a378c9-b9ce-4f1f-840b-181029edb6cd	Mazda	Biante
09f86bce-cb6f-45dc-a8c3-9499d62ddcd1	Mazda	CX-3
866d4eeb-54ce-4d96-9549-f15cdd5fb5ad	Mazda	CX-30
64d526db-e939-46c8-9a39-bee8287eb57e	Mazda	CX-5
93e59cfe-576b-488f-b759-79064111311d	Mazda	CX-50
ea9a94e6-45e5-441d-9da7-e5070c530699	Mazda	CX-60
6cf7f004-8015-4b34-848c-72d033228064	Mazda	CX-6e
0ba0ecd7-7a37-4563-9432-cd9ed712693d	Mazda	CX-7
0868fb9c-ceba-47e5-9b60-738de0689afd	Mazda	CX-70
a4e5dccb-bbef-4420-8bb8-5e5e5a6ff787	Mazda	CX-8
3e1b83a2-2fd3-43d2-a5b4-d13823704b33	Mazda	CX-80
9f692de2-4b18-4af5-aebd-5d5853d14960	Mazda	CX-9
74a204b2-625d-4938-8984-e6bdd6da598b	Mazda	CX-90
2cb343e8-dbea-4339-aecb-f09edec3bbcd	Mazda	Flair
8a1f8337-d8e2-479f-949c-80463e796f06	Mazda	Flairwagon
4ffb260e-d322-48a0-a28a-42fc2bcefc37	Mazda	MX-3
ff4cc27f-2eeb-4afb-89d4-8480d600e410	Mazda	MX-30
4f95901e-e999-4506-93f7-946b1120ac0d	Mazda	MX-5 / Miata
8976a0a3-3843-4f27-b99b-2370527582de	Mazda	MX-6
1677a055-9df8-4025-9b1c-0d1f2de64af7	Mazda	RX-7
c0c242c5-bd00-4280-88a7-eb93ce788b04	Mazda	RX-8
97fd28e3-4ecf-4fc2-8b56-a2552670d3ca	Mazda	Tribute
afed11c9-bdb4-4e1d-b020-45e7f963e9d6	Mazda	Verisa
288afc12-589f-4be5-a560-d9edffbeb92e	Mazda	Xedos 6
42c67a8d-c67f-4c14-b1d5-3c261abc6013	Mazda	Xedos 9
468ebb72-705d-4133-98b3-002305b205b4	McLaren	12C GT Sprint
315a19bb-6faf-4e70-a7c0-c451dd37b8cd	McLaren	540C
ad0ac758-db87-4588-9b07-13331076d502	McLaren	570GT
0212ba52-2f86-40ff-b126-b413022868ba	McLaren	570S
00939354-2a6c-4d83-92aa-93a34f07e289	McLaren	570S Spider
7245e2a8-fd84-4045-b8d5-cddb6d548b92	McLaren	600LT
a66eaa8a-5bc1-464d-ac7b-0ee9ae2b8d00	McLaren	620R
eee3dda5-b108-4c2e-ac23-589fce03855b	McLaren	650S
f3649b05-777c-4ace-bbc0-3f8b8eed7aed	McLaren	650S Spider
728786cb-c292-4095-a351-c70f8eef1abf	McLaren	675LT
99c1be9f-50fe-427b-be99-e317c2e27bb3	McLaren	675LT Spider
a0ca344f-a03e-4991-b20f-23c213f23e8d	McLaren	720S
5dc4ea2a-5bc7-4480-94c5-6f01c1776698	McLaren	750S
b10e9d83-db7f-4bf1-a1a3-5a2d484d48fd	McLaren	765LT
aeceac2d-510a-48f8-9987-7df90b5d1273	McLaren	Artura
34b35e5f-2933-4d9c-a568-c09e32389a22	McLaren	Elva
84ede738-df3d-4e61-a909-7d6e716dbb9b	McLaren	F1
9ee9e16f-f2ba-4258-a2fc-8516628da21a	McLaren	F1 GT
0bf8efb1-3597-4599-83fd-3ac1b64f4855	McLaren	F1 LM
a58a1845-294b-4913-a2d3-08854f03c74e	McLaren	GT
5a9def7c-fb8f-4e8a-b0b3-ac5f95697581	McLaren	GTS
1652d856-fd55-41cb-a19d-0882f79caee3	McLaren	MP4-12C
ddc9ee59-f0e4-44c6-93fc-0c0e3d117ad4	McLaren	MP4-12C Spider
209bbb8a-2a0e-4937-8f56-72e74424e7c6	McLaren	P1
d7986fd7-99de-4127-8f06-b24ac07877ef	McLaren	Senna
0232e9f0-9ee4-4bce-9cb6-d8e512ad2b1b	McLaren	Speedtail
ee58f33f-0c00-4b38-aa72-a036d5f1f32f	Mercedes-Benz	A-Class Sedan
b99fe129-57c7-4437-8519-acde489631f5	Mercedes-Benz	A-Klasse
2dd4419a-5452-4269-a001-c7efd9a8c156	Mercedes-Benz	A-Klasse AMG
bcdf0488-4d29-4828-823c-bdaabaeff0f2	Mercedes-Benz	A-Klasse Coupe
fec032cd-e069-43b0-9cca-a7532bdfc54c	Mercedes-Benz	A-Klasse Saloon
c33b7ef4-6cd7-4449-b994-c4ec317937ad	Mercedes-Benz	B-Klasse
5f9bf4d3-3028-4c4a-97d6-c331e2100531	Mercedes-Benz	C-Class Cabriolet
938f3c8d-a6c9-49e5-8adb-3948eb14d1a0	Mercedes-Benz	C-Class EV
e8e6c74d-2616-4f8f-82ed-7dacd72e437f	Mercedes-Benz	C-Klasse AMG
ab553d88-8268-4720-9d87-296e80263e85	Mercedes-Benz	C-Klasse All-Terrain
cc96f2f4-9ca4-41ac-b17b-5ab84a7d3adb	Mercedes-Benz	C-Klasse Coupe
306bd691-5447-40bc-959c-a48cfe39ee79	Mercedes-Benz	C-Klasse Coupe AMG
e0ed9282-6d78-4302-8897-3e7222ab79f7	Mercedes-Benz	C-Klasse SportCoupe AMG
0707f0a3-665d-4969-a89b-ac75fe392ea2	Mercedes-Benz	C-Klasse SportCoupe/CLC
41dda72f-00d0-4bd2-b558-ac9b1d22f6f9	Mercedes-Benz	C-Klasse T-Modell
1a14a587-f0e1-4114-86bb-030202f4791a	Mercedes-Benz	C-Klasse T-Modell AMG
5d4a1a32-9f33-4ff5-84df-99b450c760d1	Mercedes-Benz	C-Klasse and predecessors
e5c81931-0e05-4973-9cfa-7a269a3b8d71	Mercedes-Benz	CL AMG
b5c11752-ee3e-4b11-a203-bbd064c6825e	Mercedes-Benz	CL-Klasse and predecessors
6723d79d-4546-41b9-95d4-efb6cf0de717	Mercedes-Benz	CLA 45
f17669e5-0e60-4b62-8327-39f8f9cab3b4	Mercedes-Benz	CLA Klasse
aa71b4d4-a427-4e2a-bb8a-475f42f83f69	Mercedes-Benz	CLA Shooting Brake
ce8a83c0-beac-4de4-878a-9cfbc57734c3	Mercedes-Benz	CLE Cabriolet
c1f70d13-83ca-43fd-915d-f5f227aefde8	Mercedes-Benz	CLE Coupe
1991a64d-8523-4ecc-919a-d04948febb81	Mercedes-Benz	CLK AMG
d95c25aa-542f-4b9a-9521-9c99a3b1cc2f	Mercedes-Benz	CLK AMG Cabrio
d7793dfa-9cdf-4a6f-8fc3-41014286d5a3	Mercedes-Benz	CLK GTR
41f9f810-35f1-45ff-aecf-7fe53da5f774	Mercedes-Benz	CLS AMG
16af3b52-b9d8-4759-a458-8ffcbfdc1a62	Mercedes-Benz	CLS Shooting Brake
a827f2d4-d301-49f8-a11d-fef5cf1444e4	Mercedes-Benz	CLS Shooting Brake AMG
4bd887ce-ab0b-4115-8617-e5fe97dc2f32	Mercedes-Benz	CLS-Klasse
fba22913-0835-4d1d-892d-613ead990dba	Mercedes-Benz	Citan
df1b0653-b06c-453e-be74-060f7fccb598	Mercedes-Benz	E-Class T-Modell All-Terrain
a804fc9a-52d0-4996-aa00-351f5cbc10a6	Mercedes-Benz	E-Klasse AMG
9f03c68b-9df9-4a1c-8686-f883d10c0830	Mercedes-Benz	E-Klasse Cabriolet and predecessors
ae424223-1c5a-4a60-a5cd-0cdbc243000b	Mercedes-Benz	E-Klasse Coupe and predecessors
8fd49485-9aad-44cb-a0ad-9faf98a0c71f	Mercedes-Benz	E-Klasse T-Modell
62b4d4ff-9b9c-4efd-88ea-937baedbcca2	Mercedes-Benz	E-Klasse T-Modell AMG
456d14ca-30bb-4657-b046-f3c28b10d9ff	Mercedes-Benz	E-Klasse and predecessors
7eb694b1-4cd7-43c1-b375-b258530119d2	Mercedes-Benz	EQA
122e2532-8b36-4680-8ea7-78284960af49	Mercedes-Benz	EQB
20f2ed8b-de5e-4214-ad5c-dece3245a124	Mercedes-Benz	EQC
763c51d5-9b94-4d2e-91eb-89eba7556c2d	Mercedes-Benz	EQE
da18fabe-a2b1-48ad-81d9-5a176bb20fd5	Mercedes-Benz	EQE SUV
6c14bd73-6fab-422a-a2be-67967a36f4d6	Mercedes-Benz	EQS
f2aae69f-5d73-40ec-846a-af8519ee8bf2	Mercedes-Benz	EQS SUV
c35b03e7-72df-4193-9ea9-d13161d0ba18	Mercedes-Benz	EQS SUV Maybach
f8610822-02e6-40bc-ad5e-05ccbe816659	Mercedes-Benz	EQT
6d3a2278-f3be-4d07-987f-16ce07f69474	Mercedes-Benz	EQV
3c825db7-1e77-42f2-8dad-7969883906fd	Mercedes-Benz	G-Klasse
6167216e-8af6-47da-a41a-a787f7650e75	Mercedes-Benz	G-Klasse AMG
9d8d2af5-e640-4e23-8cef-f57b94d41892	Mercedes-Benz	G-Klasse Cabriolet
84081f10-fd71-42e7-8e33-594115a6f933	Mercedes-Benz	G-Klasse Kurz
1c5f9c4a-8b2c-4fee-9f82-90082e0c7ff9	Mercedes-Benz	G1, G4, G5, L 1500 A, 170 VL, 170 VK
258d2919-8266-4c9d-a26a-774b59a4af27	Mercedes-Benz	GL-Klasse
8fefe2cd-8057-4c44-8e7f-35f7c4541623	Mercedes-Benz	GL-Klasse AMG
ba5dbca1-3fea-4d64-b012-df051c110c18	Mercedes-Benz	GLA
76bf102d-9949-4f19-88e7-8ce627a0c8bd	Mercedes-Benz	GLB
740025e0-9f76-4e58-a41a-1a9ccfcceeb6	Mercedes-Benz	GLC Class
795150ea-ca41-4132-9e42-015ee599a539	Mercedes-Benz	GLC Class Coupe
7fbf9f00-1363-4f22-93fc-e6e544a6f0d3	Mercedes-Benz	GLE-Class
1a24ef76-a2c8-4a05-95fb-055df7b8ecf9	Mercedes-Benz	GLE-Class Coupe
765ba2f3-a5fd-44f4-ba1f-f02d0e391edd	Mercedes-Benz	GLK-Klasse
fdb48dc0-6598-421c-aaaf-5e7179803816	Mercedes-Benz	GLS Maybach
48a13e1e-7f44-40da-8cdf-ec5ee721fd5d	Mercedes-Benz	GLS-Class
b03f16d5-cd7c-445b-bdbf-be96001ebd71	Mercedes-Benz	Laundalet
84860285-9e0a-4a7b-b24c-8b876a0c5e14	Mercedes-Benz	M-Klasse
d39b11f4-4692-4f51-b613-c9e063abf878	Mercedes-Benz	M-Klasse AMG
7ab820ad-d40c-4be1-bc63-2ba23b94e65b	Mercedes-Benz	Maybach EQS SUV
80cd0bc1-6438-428d-a865-7fd179ee4496	Mercedes-Benz	Pullman
511c1cef-1e3e-497c-817a-9b758832ef16	Mercedes-Benz	R-Klasse
abf4d868-0381-493c-9d1f-4a039a3d47e7	Mercedes-Benz	R-Klasse AMG
3ff55b86-2a48-4c31-8390-17cb7fada1a4	Mercedes-Benz	R-Klasse Lang
99b63733-fe94-4aa4-afae-9b62795e9820	Mercedes-Benz	S-Class Cabriolet
0a82c6ff-a888-4218-80d4-dd8645e3a5b5	Mercedes-Benz	S-Class Coupe AMG
a0180589-9357-47bc-9c6c-c83226166f03	Mercedes-Benz	S-Class Maybach
23ee3352-3b3b-4385-90c7-f87425720f71	Mercedes-Benz	S-Klasse AMG
159b1949-583e-446d-9681-c3bcf0658c51	Mercedes-Benz	S-Klasse Coupe
019b0ed8-e4f7-4c84-85ad-9854045929a4	Mercedes-Benz	S-Klasse and predecessors
ab57d048-b40c-411a-926f-48430508b642	Mercedes-Benz	SL AMG
847a8df2-3ae0-474f-b5dd-b558862439e3	Mercedes-Benz	SL Monogram Maybach
4d6b2ccd-09b1-404a-9f0d-bcc25c61ab4d	Mercedes-Benz	SL-Klasse
0947220c-b618-4d6e-a076-0bf384c22bc8	Mercedes-Benz	SLC-Class
0c06bae8-b8b9-4546-b503-88ac3ef7c8ec	Mercedes-Benz	SLK AMG
c8338511-650c-4261-abef-9a603195d36b	Mercedes-Benz	SLK-Klasse
d5d0328d-7ab4-4c2b-ab0c-c9b6d7dd9a37	Mercedes-Benz	SLR McLaren
ff528559-5e19-4fc2-a828-5ac680edd59f	Mercedes-Benz	SLS AMG
2293817c-7028-481d-9e29-ac33da77812a	Mercedes-Benz	T-Class
6cb5d0b8-cce9-4671-bb61-3c5e015b54a1	Mercedes-Benz	Typ 12/55 - 14/60
b9eb0574-96ff-4f99-a1ae-1c9fab51bef5	Mercedes-Benz	Typ 130, 150 and 170 H
cd557d83-a212-4ef5-aaae-685d225308cf	Mercedes-Benz	Typ 170/170 V
2c318c2a-7ecc-46b5-85b8-0ad9801cab27	Mercedes-Benz	Typ 200
384b441c-90af-426d-9986-9a3a53b3a4cf	Mercedes-Benz	Typ 230, 260 D
0b2cab1a-29d5-43ec-a6f4-0f2a0f8fbc6a	Mercedes-Benz	Typ 290
1e13699b-9917-4f97-9ed8-79612e9a5ea5	Mercedes-Benz	Typ 320
c2f2ce86-5e8d-449c-8c25-145066b5ce5a	Mercedes-Benz	Typ 380
1752911e-a2af-486e-83de-03e33b74ef3b	Mercedes-Benz	Typ 500 K/ 540 K
92726c82-df92-4b7f-b0d8-b60c0b34d116	Mercedes-Benz	Typ 770
33affb3b-a607-4f1a-8147-99c69686eb43	Mercedes-Benz	Typ 8/38
efdf66ec-32f3-45d9-80a8-af29039fd626	Mercedes-Benz	Typ Mannheim
5d56ed64-1a3a-41ae-b02c-bb4286837bef	Mercedes-Benz	Typ Nurburg
905d4ff0-711a-4bd9-a845-4818dd4a36a6	Mercedes-Benz	Typ S, SS, SSK, SSKL
40426a13-a6e5-4c92-b96b-7f8047683fb0	Mercedes-Benz	Typ Stuttgart
ef1623b1-1fcb-4127-81f1-d32735a2cf94	Mercedes-Benz	V-Class and predecessors
84f8ad08-c274-4381-bd7f-8ba2ba3113b1	Mercedes-Benz	VANEO
c0202abc-fa8f-492b-9e29-0118fd6e2fbb	Mercedes-Benz	VIANO
01d6f9b7-fbd6-459b-8804-24ba69f2c7ff	Mercedes-Benz	VLE
bea532a6-8e02-4023-8866-dcdec429884f	Mercedes-Benz	Vito
46c9e856-c871-4fa3-859f-25ed4e3b20f9	Mercedes-Benz	X-Class
228f14a3-65b0-4803-9d56-55a3395bf218	Mercury	Cougar
fa6a051b-6032-475d-b595-f2d32af2cef5	Mercury	Grand Marquis
abd06113-76bc-493f-9106-a506b904920a	Mercury	Marauder
1168d037-9043-452d-b21a-5da09d984389	Mercury	Mariner
11a71617-eb38-4426-a641-022ba7a8de67	Mercury	Milan
9406ccf0-2d9e-4a00-90a3-4f2f37647d74	Mercury	Montego
08b58441-85b3-47aa-aa11-eea35c7f45e6	Mercury	Monterey
44a98e35-2a9c-4dd6-b376-d1759bfe2ccc	Mercury	Mountaineer
f7d17fa3-ac95-4e8b-9db6-1c56657f8b6f	Mercury	Sable
0b96d5c9-01f7-4582-9c86-08eb8c309282	Mercury	Villager
c6b90426-129f-486a-ae73-942410a2e5ac	Mitsubishi	3000 GT
e5816d37-d251-4aef-9047-4c957d8dae35	Mitsubishi	ASX / RVR / Outlander Sport
a70cd848-da8e-4b39-9eaa-73d8baf0f1b9	Mitsubishi	Attrage
2f3cbed3-78dc-49cd-84c6-5e487f3dda38	Mitsubishi	Carisma Sedan
5606135c-1268-4956-8ad4-1a64e82436ae	Mitsubishi	Colt 3 Doors
75c54f21-1447-42ad-a88d-5db16dac4fe5	Mitsubishi	Colt 5 Doors
f5a5340d-f0f5-4283-a0f4-c14656dd37f3	Mitsubishi	Colt CZC
0df07e74-cbc1-4570-8a26-4f2a9cbf902f	Mitsubishi	Eclipse
7384240a-77d9-43ee-a7f5-b428006faea8	Mitsubishi	Eclipse Cross
95abb22f-52eb-403d-96df-b5aa30cdce83	Mitsubishi	Eclipse Spyder
ed6d7534-e214-4127-b6d4-e73eb2521cd1	Mitsubishi	Endeavor
92950d5b-8e60-479f-86fd-184fbaada975	Mitsubishi	Galant
9ca4fdfd-3aad-4667-93ae-8ec803bf2e20	Mitsubishi	Galant Station Wagon
abe7e569-5a5e-4dbc-872b-b0daf07b3c8b	Mitsubishi	Grandis
0f0df17e-b558-4109-a54c-d49cd7d164d8	Mitsubishi	L 200
45bf0db3-ddee-469b-908c-d4c7d0a61af9	Mitsubishi	L300
01d6998e-eb15-4489-b490-4e25530774d0	Mitsubishi	Lancer
fd4bfa50-5d97-4dc0-9cc3-2e5d44a01301	Mitsubishi	Lancer Combi
d8712880-12f2-493a-add4-7a276b9d17cf	Mitsubishi	Lancer Evolution
931dc387-d136-48c0-ad09-491a734b4e2a	Mitsubishi	Lancer Hatchback
3869013e-ea02-4a51-8a24-e637252d745c	Mitsubishi	Mirage
4b5edf8f-ab00-4cd2-8e96-580909e79a96	Mitsubishi	Mirage G4
25f9ec57-611a-4704-a72b-ac8be88a9efa	Mitsubishi	Outlander / Airtrek
9265f719-ff6b-45e0-a6f3-1771b6c08b83	Mitsubishi	Pajero 3 Doors
125d7bec-bb74-4d39-9f5c-fc26602ece48	Mitsubishi	Pajero 5 Doors
56f2baf2-db23-4e7a-822f-203a481f2e35	Mitsubishi	Pajero Pinin / Shogun Pinin / Montero iO
b343340e-4d65-476e-a966-57093d0fb7c8	Mitsubishi	Raider Crew Cab
75269088-74a3-4ae2-82b6-c97a351bddb0	Mitsubishi	Raider Double Cab
0d919d8c-c84a-44b7-9096-7abd8bd9dc0e	Mitsubishi	Sigma
dfe8804b-f518-45bf-91ef-65fd24975b5e	Mitsubishi	Space Runner
1ed3b50a-7cab-4a78-b57f-d38692861a99	Mitsubishi	Space Star
0f9ecb36-a258-487b-ae16-560339703280	Mitsubishi	Starion
c68d7e1c-b78c-4d85-b9cc-cca8d3a78280	Mitsubishi	i-MiEV
c5c07a4b-3f07-4fa4-9ea3-9fd1d1896ed4	Morgan	3 wheeler
463f6d50-22cd-45e5-9202-897e0e9539e7	Morgan	4/4 2 Seater
abeee219-a80e-483c-8c0d-25e20c39fffd	Morgan	4/4 4 Seater
a24c5627-a825-4415-bda5-ef2e59dc4804	Morgan	Aero 8
8c2aa555-45bc-4716-a075-7b0ee7b651f3	Morgan	Aero Coupe
7b9d1c78-671d-4291-a2a4-509b502b9215	Morgan	Aero SuperSports
0cc8af85-7ac6-47f8-935f-f1cdcdaeb783	Morgan	AeroMax
b52fba06-3919-4beb-9c73-f17240ffa20a	Morgan	Plus 4
c66d1d16-87a0-4d5b-b554-11a3d567ac89	Morgan	Plus 8
9fc7b09d-d588-4a25-9406-dfff4f888d94	Morgan	Plus Six
682c0602-593a-4750-88e0-3a9c662b56b1	Morgan	Roadster
7175e2ee-2901-4b9c-900f-b457825838ea	NIO	EC6
6b0b928b-e348-4cc9-9582-5385ef6a08d2	NIO	EP9
cd0ce8e4-5815-4297-8633-dd2bd205f4bf	NIO	ES6
e7054edd-2497-4279-9874-c6325112ac80	NIO	ES8
d097963f-15fa-4de3-9c32-8aec6a2dafcf	NIO	ET7
ec7a1ca4-e33e-462b-91ec-42ba48a20539	Nissan	100 NX
fc142a45-48d7-47e8-8496-76c7b56d21f6	Nissan	200 SX
da8d5e89-459b-488b-a7b5-cbedab424222	Nissan	300 ZX
27861ab0-7383-4a19-a758-aad985bbc0b2	Nissan	350Z
c5dea2ee-6153-4a47-b09b-bf64874f30b7	Nissan	350Z Roadster
b5550bab-cca5-4b96-a66e-07832e837f80	Nissan	370Z
821d4de0-3478-41fb-959a-ed75600d8a99	Nissan	370Z Nismo
530fbb36-cf8c-4109-b903-92e044e85b33	Nissan	370Z Roadster
1772b30f-70e9-4a3d-b5c6-e9f5391ac04c	Nissan	Almera / Pulsar 3 Doors
fdd0a79f-6c9e-4b9c-a5d3-37b35dfd0f17	Nissan	Almera / Pulsar 5 Doors
2d38784f-fec0-4396-a607-77946be63846	Nissan	Almera / Pulsar Sedan
a5ef54ad-2f97-4562-84f0-fc9464c859d9	Nissan	Almera Tino
69189b6d-500e-4a84-834d-77bed5ab635e	Nissan	Altima
10899996-6815-4ea4-ae63-be39081055ab	Nissan	Ariya
5fae7ebc-2c47-4553-b469-e299d45910f3	Nissan	Armada
a4aa2138-1977-47ea-8d7b-25a5d086e9b2	Nissan	Bluebird Hatchback
6eb3d032-1035-4cb4-8b38-6d8aa81bdfb2	Nissan	Bluebird Sedan
46752ee4-5bf7-4709-b7ed-0847809cae43	Nissan	Bluebird Traveller
430c8b19-4b94-4fbc-a6b5-91940f4128ec	Nissan	Cube
da35021d-31a3-4a5d-8f5e-45567e32bed3	Nissan	Elgrand
15cbd71d-39a1-4865-8929-b8d034864790	Nissan	GT-R
40721a74-05fd-4d48-9d2f-8ddfe85121cd	Nissan	GT-R Nismo
eec30249-b69c-4411-a1bd-b52c0734bd61	Nissan	Grand Livina
41a3f78a-e0ad-477e-a72a-206407a04107	Nissan	Juke
1e7d396d-bc4b-4aa4-abf6-e3e7af5c8aa7	Nissan	Juke EV
04f3279d-f642-4cb8-9958-b439970d075d	Nissan	Juke Nismo
87036ad2-6948-4ecc-8f48-323398d52c6f	Nissan	Kicks
4f3a1ea9-3521-4627-9a6e-5ecd3464f8c2	Nissan	Leaf
a2c4bdcd-1e97-4fff-b412-21d797209873	Nissan	Liberty
56475ce9-39b4-4e30-b146-7f10390e3e7d	Nissan	Maxima
62eb3c67-4524-49f0-bafb-04cd27e66c3e	Nissan	Micra 3 Doors
2b76bacc-2d88-4df0-9f2a-81506d7b40c2	Nissan	Micra 5 Doors
5fba8f10-4a69-4135-ab8a-baccae3dfacd	Nissan	Micra C+C
265bad9c-57f9-4a54-832d-b8725193a8eb	Nissan	Murano
4a014eeb-ed47-44b6-99ab-4ca7f8e76eb6	Nissan	Murano CrossCabriolet
2a01053e-c594-4e0b-b88a-aab683c31eb6	Nissan	N7
f354dc4b-40b1-4e80-abf8-a6a847b001bb	Nissan	NP300
20a80a73-d606-46c8-a43a-6cb4ef4745ec	Nissan	NV
ed16ae88-2045-4991-a928-8c8846987908	Nissan	Navara / Frontier Double Cab
532c3f19-b2c0-45c4-933b-e03835a13bd5	Nissan	Navara / Frontier King Cab
7826e354-6b8d-4f33-b6f3-6886c013bca8	Nissan	Note
55e52c42-73f6-4b8b-bf08-7b3f7280f185	Nissan	Pathfinder
b7f110e8-1693-460a-b5eb-9eb5c9c2e5c8	Nissan	Patrol LWB
909dd05b-9438-4b07-b3e8-7c50da1c759b	Nissan	Patrol SWB
204270c6-2bb9-44e4-a87e-641b5f2f8905	Nissan	Pixo
bc5ea0ce-c7c0-40c9-822b-b735ef68b8a1	Nissan	Platina
1131dbf1-ab91-4d0f-a3c5-db794b52a6ba	Nissan	Prairie
96b7987a-1ced-4d01-a637-fdb835e3fa7e	Nissan	Primera Break
2655c3d4-201e-4d1a-bea8-36a4da787b58	Nissan	Primera Hatchback
43b2a9ce-5698-4873-9f37-f4a02abc01cd	Nissan	Primera Sedan
5a53a527-7ad8-46e5-8307-60193d725e4a	Nissan	Qashqai
9840b340-c4be-45e4-80f4-3f19f8d0defd	Nissan	Quest
810b3f21-171c-483f-9c0d-c7dd259bc515	Nissan	Rogue
97851937-d62e-4071-95fe-5cf4f9fc227e	Nissan	Sakura
5485054e-d202-4151-bf7c-3ef7d81c07ac	Nissan	Sentra
f19765ca-5e49-4416-84e7-5d686fdeb637	Nissan	Serena
e65e67fb-41ea-4cd8-9a39-f252d751acb4	Nissan	Skyline
35fbcbdc-cda5-45f3-bf79-ea0ae51292a5	Nissan	Sunny
d7fae519-308d-41ed-919a-88c95497ecd3	Nissan	Sunny 3 Doors
3e552a63-efa2-484b-9c45-bb8cf909fd7d	Nissan	Sunny Hatchback
66d4cc9f-f605-4fdb-8856-e588618d1bb1	Nissan	Sunny Sedan
2b1fbddf-4271-4205-94d0-89729b6af1ac	Nissan	Sunny Traveller
2db97794-f2b7-40da-9732-23fffa42b356	Nissan	Teana
6f90e8bd-481d-4610-a957-66c814beaedb	Nissan	Terrano 3 Doors
74ef7357-c761-4fbb-bb6c-5e877e0a7545	Nissan	Terrano 5 Doors
83999bc8-6974-4274-9212-69727143c87c	Nissan	Tiida / Versa
d3d3f296-c8fa-4e44-8722-0f9e3d845998	Nissan	Tiida / Versa Sedan
7f7cd4f7-5deb-444b-bd92-e7a3c8b39837	Nissan	Titan Crew Cab
8442e4ca-fa2f-41e9-827b-6ad9415cce53	Nissan	Titan King Cab
14a8684f-2f4c-4ff9-9683-2a9086d1945a	Nissan	X-Trail
f35cb0ed-f6f6-4ac8-a9fb-217f9a4875ee	Nissan	XTerra
04a3fcd6-a8dc-4013-ad6d-7be85ed54850	Nissan	Z Nismo
3b1f6df6-e4e2-49b1-8fb0-8a109c953f5c	Oldsmobile	442 Convertible
9d9e66b5-a5f2-4d93-bbcf-ccf132b631fc	Oldsmobile	88
918d551b-defd-4ded-8469-4d24692e3992	Oldsmobile	Alero coupe
24b2cc12-7dae-4790-8bc8-83d5f0a71ba7	Oldsmobile	Alero sedan
67385975-56eb-45ce-b5d2-6850e6d125ec	Oldsmobile	Aurora
b851ac71-1c8a-443f-bd83-0861f0972dce	Oldsmobile	Bravada
b67434e6-9b2e-4cf6-a702-f5eba97b8b79	Oldsmobile	Curved Dash
f711f503-d326-4402-bda1-53baf6588d78	Oldsmobile	Cutlass
4d4ac022-27d7-4c4b-be70-42ce02d2db10	Oldsmobile	Intrigue
6735eede-b35e-41ca-b585-c4aef2091ab4	Oldsmobile	Silhouette
59d40ea4-b7da-48ad-a641-171c4417775f	Oldsmobile	Toronado
17a4de32-4eb1-4e62-8e84-09bb987ca20a	Opel	Adam
8338f710-e092-49ad-8e0a-d1fcd17b3342	Opel	Adam Rocks
df28d74f-33b1-43fa-a076-47a05b502181	Opel	Agila
8dc3b16b-4e2a-4334-a1c2-ea738c90dbc8	Opel	Ampera
14f842c8-26a3-424a-ba2e-015363339e20	Opel	Antara
a57119b2-24f3-4cee-8288-94edeb5ec376	Opel	Astra 3 Doors
f8f23d0a-27c6-4139-b174-8b2db356582f	Opel	Astra 5 Doors
ce5fd76c-3c72-4d20-9e11-090d6f27fd8a	Opel	Astra Cabriolet
12b2f085-f550-44a4-902a-feb2602e94f3	Opel	Astra Caravan
d6c597b1-d96c-45fa-8f52-ea25a76339be	Opel	Astra Coupe
18d5b774-12ac-45ef-8e7c-4cefc8ca1a73	Opel	Astra GTC
e9d5da36-aedb-4807-bc39-5115f04065b2	Opel	Astra OPC
485ee7ed-334e-4ca9-9590-e1ef134c6b5a	Opel	Astra Sedan
c3fdde7e-54c0-47ac-b9ab-60c8cb55064c	Opel	Astra Sports Tourer
2133c0ca-9a51-4514-a682-e3cfdd8261a9	Opel	Astra Twin Top / Cabriolet
ba7db120-81d5-4b05-b1b3-55ef2d8e96d5	Opel	Calibra
eb25b198-640b-4e1a-bc88-a4c3e8018a97	Opel	Cascada
e39410da-5208-4bfe-824c-4927dc2aa573	Opel	Combo
f32e52ce-50a7-46fa-97b8-b0d4a830d487	Opel	Corsa 3 Doors
02613735-bb94-42f7-bb0d-96b3775776e3	Opel	Corsa 5 Doors
a96cf041-9dd6-44c7-a578-3be05461b8b5	Opel	Corsa OPC
660edf69-05c3-4d0f-a3ca-9416c965b727	Opel	Crossland
bf843684-f096-4aab-80ba-d06e90d5de91	Opel	Frontera
176220b2-2719-496f-805e-6eb10be6daf1	Opel	GT
7f55d173-1d9e-47da-8c60-71d88ee534b2	Opel	Grandland
3a6da779-563f-49b1-9746-0a40b7bb930f	Opel	Insignia
dc510ba7-09c5-417c-a28c-c611da44c71c	Opel	Insignia Country Tourer
0401ff45-d65a-41b1-b44d-6b1553247e15	Opel	Insignia GSi
48fe757a-1a77-4360-ad2b-48263aedcc0d	Opel	Insignia OPC
934bd35f-f14b-4dc9-9962-24d83433f493	Opel	Insignia Sports Tourer
5977a029-3130-473c-8c83-c2ec119fc2fd	Opel	Insignia Sports Tourer GSi
33840de7-b9e8-4443-afdf-f75612ace70a	Opel	Insignia Sports Tourer OPC
bc9ec8a2-c34b-4db9-81b9-aac66ae44ebd	Opel	Kadett 3 Doors
5e199b4f-9c2a-476e-bc01-29b2b6266740	Opel	Kadett 5 Doors
7a91837d-fb01-48af-9105-0d8d111b28ea	Opel	Kadett Cabriolet
3949220d-e4f7-4217-8d41-7df8b386b243	Opel	Kadett Caravan
982786ea-2cb2-4d43-afa9-2e4c200cda1e	Opel	Kadett Sedan
6178d845-ee85-45d0-8bb2-40f7a147c6a6	Opel	Karl
a62610ba-ac1d-4de0-ab54-3e1215a42df3	Opel	Manta
c82d3d5d-04b7-4eb7-bc9a-be8e780d3a0c	Opel	Meriva
a080eaa9-0358-47c9-86a2-eb641dd98f33	Opel	Mokka
7f613bd9-748d-46bf-8421-b2c50895557a	Opel	Monterey
194717e6-0a01-4225-a15e-f70c7a312558	Opel	Monza
a47b4d13-8f5e-479c-99d7-0462c17089b8	Opel	Omega Caravan
e014e645-0b5a-435e-a3e2-a7b2d99840ef	Opel	Omega Sedan
a068815c-476c-4501-967f-08416ad0884b	Opel	Rekord Caravan
55b7a904-73ec-4953-8d3d-9db886b06a92	Opel	Rekord Sedan
44aea6fa-d428-4284-90d1-03208e47d4bd	Opel	Rocks-e
40e91add-75f9-4a6a-8cd5-d1b358619176	Opel	Senator
2a13f7c3-c391-4c39-8859-85f49d42033d	Opel	Signum
e54698b1-2d03-4918-ace9-bf3a15b2dc7e	Opel	Sintra
9c2ed761-75d4-4ae7-913b-2331b0c9dbb1	Opel	Speedster
41748842-d977-466c-99cf-b05559d63f0e	Opel	Tigra
b660f8c4-4c83-4117-b88e-f2eebdbaa169	Opel	Vectra Caravan
09aa14a6-d755-4041-adb8-f2ba731b5bb3	Opel	Vectra Hatchback
e2aa60a1-7d6d-424a-9bec-08ff77649121	Opel	Vectra OPC
74740f11-1f33-4e22-8d16-76864a2fd452	Opel	Vectra Sedan
5e3d564d-8c78-4e8c-993a-7a0d3a5649b8	Opel	Zafira
5c69be06-7745-42e1-800c-4c56702f0ff0	Pagani	Huayra
f7dd37e7-ce75-43e5-95cc-293c1a3c6ff9	Pagani	Huayra Roadster
888cf778-8429-46ae-bb86-41d8d55fd411	Pagani	Imola
8c0492df-2b9a-4897-a24d-4851def3fe82	Pagani	Utopia
4a0b473d-54f2-4ade-b984-06e108a6dd68	Pagani	Zonda Cinque
45af68bc-8864-480d-bded-3f6e7d5c4b5b	Pagani	Zonda Cinque Roadster
1a07e911-3770-4f92-ac7b-6d473c84f45c	Pagani	Zonda F
cd669af2-6c5f-4b7b-a1ea-ade76436e1af	Pagani	Zonda F Roadster
3a3c75e2-1c1c-40c3-bb4b-f666debdffb4	Pagani	Zonda Roadster
044cfc7d-4726-4bf1-ae09-c9c5351b67aa	Pagani	Zonda S
78665a6d-a4ea-42ae-9d52-0b13c6299e76	Panoz	Avezzano
984a4f94-abae-41f0-8931-88ce04ea461f	Panoz	Esperante
c91ca66a-da93-4196-bfc0-2c87a0d7a09e	Panoz	Esperante GT
88c3de34-72d8-4283-af70-05d7dcabc045	Panoz	Esperante GTLM
02bf524a-e73e-4e52-bdb6-1dcb2b760d09	Panoz	Roadster
dbbe125a-efc3-4462-8cba-27227305c102	Perodua	Alza
acd3946b-7732-4a0b-9e3f-94bfc9b79738	Perodua	Aruz
c3473c03-4fba-49fa-8ecd-814fd573ab94	Perodua	Axia
2dbf998d-6220-4d70-bd78-967dc3186e00	Perodua	Bezza
ab09dc88-cbb1-4e12-a398-bb70a3631712	Perodua	Myvi
0835f787-dce9-4c6e-b230-0a8a95ae94dd	Peugeot	1007
379215a0-af0e-4385-8817-d68cde22fc65	Peugeot	104
ae216efe-311c-4768-8392-5b8fe69ee84f	Peugeot	106
5d218700-6369-4864-bbb5-96be5727f3d2	Peugeot	107 3 Doors
df8f0a2f-0282-42d7-9bd8-07c102344e64	Peugeot	107 5 Doors
dfe6659b-24e1-4025-997d-3dc2226b6f51	Peugeot	108
fd1c9577-4648-4c6c-a333-06360ff17a78	Peugeot	2008
1cb75120-47e9-4e5f-be5c-a15f6d2c2c7d	Peugeot	205
6ee59bbc-7b7e-45e1-8323-5a1bf323bd71	Peugeot	206 3 Doors
2a6ff7af-b0f4-41ee-8d44-2cb689f6be57	Peugeot	206 5 Doors
70996dff-cb38-48fc-8a71-0398b46d799e	Peugeot	206 CC
f7e04d5a-cded-41bb-8a1b-e479f6aa2221	Peugeot	206 SW
3eca8271-3b60-4f5e-9e0d-b80f0b14323a	Peugeot	206 Sedan
191ccbbe-8315-4fb2-a6cb-8f0af46cbbd5	Peugeot	206+ 3 Doors
59d1ff10-999c-40b2-8cc9-f5962b36a7b1	Peugeot	206+ 5 Doors
826d99c4-45ca-4769-be44-b5ec297c9a76	Peugeot	207 3 Doors
a23e86f1-40b8-4f25-a033-1f65a18988a8	Peugeot	207 5 Doors
8666bf35-79a5-4a8e-88a2-8496b218298c	Peugeot	207 CC
35b67f6a-fd29-41fd-af0f-369f83d7be4b	Peugeot	207 SW
3caa3b5a-417d-4302-a07f-9ecacd71b9c2	Peugeot	208 3 doors
1466c0d9-eb9f-47fc-a950-32cb018ac4d6	Peugeot	208 5 doors
936caa0b-287c-43ec-b1c8-8d37f4b14483	Peugeot	208 GTI
4ce946ab-1b73-4590-92a0-933efaf9f1ed	Peugeot	208 XY
a115a57a-352f-43b4-b694-e6b77cd4b3ee	Peugeot	3008
0163103c-4c39-4015-9a63-3f07e3705e3a	Peugeot	301
5a4aa981-91c2-4b8a-aded-a9679e91e89c	Peugeot	306
58b87d8a-e23c-4064-b984-5f32ef0e4823	Peugeot	307 3 Doors
96579e65-b5ca-461a-a287-d83bf256661a	Peugeot	307 5 Doors
33789c13-c759-45a1-9ee1-cc318e54998a	Peugeot	307 CC
3bfd218a-fe56-4587-80d6-5f966772f95d	Peugeot	307 SW
5fb26798-f6d2-46a6-91b0-72dcff11552e	Peugeot	307 Sedan
aa910ebf-db18-49e8-9bae-0c7c3a73368d	Peugeot	308 3 Doors
117ed27b-ce3c-4125-99ef-cd5a4978d8c8	Peugeot	308 5 Doors
9fc388e9-ca5d-4286-a6b8-1909302291c9	Peugeot	308 CC
57f31c56-3cfa-4df6-880a-064dd000e370	Peugeot	308 SW
8b98454c-6d5d-4153-9e52-606d45ec1288	Peugeot	4007
ab5d0a7a-b1ab-4be9-b8b6-4eee2ecae541	Peugeot	4008
965bfc2b-0def-4097-9d48-058fc026505d	Peugeot	405
795b8b75-a2c4-41dc-bc98-9c1af2b60838	Peugeot	406
1cd1e027-f02f-4753-8409-1cb567b47cea	Peugeot	406 Break
89e32f0a-35c0-4ff7-adc5-3e3cd9682b7d	Peugeot	406 Coupe
a827ac8b-3211-405c-a99b-0abfbb643163	Peugeot	407
78e7418a-15b6-4e4b-8d2c-6a810e407dc1	Peugeot	407 Coupe
d2bd0931-9bc5-40ca-9144-cd61ca5723ef	Peugeot	407 SW
77303a95-0b96-434e-83c8-d0ddfd6f3787	Peugeot	408 Fastback
acf19165-81d4-4e0d-ad3d-4d783f14d17b	Peugeot	408 Sedan
6dd8e6be-5950-40a3-afec-38d4d5983a14	Peugeot	5008
9ddfc1f6-e83e-4e80-b580-e6f28806bd28	Peugeot	504
6edf1e1d-9c34-45da-9702-d1237d969549	Peugeot	505
4d2157a9-4f13-4d9a-8edb-cfa7f1f38e7b	Peugeot	508
1db590c9-518e-49b4-9825-d1f3c83ae25d	Peugeot	508 RXH
d9f72b71-f74a-4705-89f5-3e33a0e5136d	Peugeot	508 SW
0dc123db-9904-484e-9b52-004f86eb591b	Peugeot	604
1e16422f-df82-4918-b7aa-acce6783340e	Peugeot	605
8111e163-2567-4389-94ee-4cf2e0c9ad13	Peugeot	607
f0122b45-f9c7-47ce-8634-4cf73e9c6bb0	Peugeot	806
46d3ef62-bb24-4b0f-a599-2db97203ab72	Peugeot	807
cf21a605-04c2-458f-86d0-275460ea1618	Peugeot	Hoggar
9f0b2980-5b48-4313-a462-e54b7986c113	Peugeot	Landtrek
54ec55fe-1ab9-4b54-a0d5-424d68692b48	Peugeot	Partner Combi
cd36c863-50b9-4e5d-9d43-bd1e23dfa218	Peugeot	Pick Up
8bc323e3-c4b9-45d6-8845-8b4bea40855f	Peugeot	RCZ
a11f4f1b-c20e-435a-8c06-f9bf58ff732e	Peugeot	RCZ R
da043363-74e5-49d4-89c3-6aaf254210d5	Peugeot	RIFTER
c15603ea-fde0-4a07-b47c-d03495f23c0d	Peugeot	Tepee
f4ba75f9-63f5-4932-8db0-e5a5cfddcfe7	Peugeot	Traveller
1ca5b3e9-1901-4c93-a6d2-122f666c3353	Peugeot	iOn
d228b3ec-35f5-4645-a608-b1f8be6a7014	Plymouth	ACCLAIM
87062a92-b71d-46d5-81b0-af5ce5ac8df9	Plymouth	BARRACUDA
75bb5b73-c732-45f5-8e90-c25a5e8c23a7	Plymouth	BREEZE
e911f37f-3a82-4a80-a037-e8286f0e8af2	Plymouth	LASER
ad4690bc-818f-4fd5-8904-86c96217eeae	Plymouth	NEON COUPE
72b6f151-9e88-4515-974a-30910fe953cd	Plymouth	NEON SEDAN
f450932a-00ec-4296-af50-0b93d67025fd	Plymouth	VOYAGER
2ed9e837-c25e-4754-a676-a0491e3ce9b1	Plymouth	Valiant
270a0d65-006f-41f2-82eb-6562c5bde03e	Pontiac	Aztek
d1b4f19e-aeb3-4b49-b040-a1999614ecbc	Pontiac	Bonneville
e5d4f680-36b0-4971-881e-73144099be6b	Pontiac	Fiero
6ef25468-35d5-4d7b-9f17-486f7862f095	Pontiac	Firebird
a0b0877a-d145-4293-ace8-15af025ef486	Pontiac	G3
8c95fd42-c7e9-4670-b4aa-997c607693bf	Pontiac	G5
f8bd3196-8eb5-407f-99bb-90977a76368b	Pontiac	G6
7533843f-067a-41a0-a4dc-5a09f600be94	Pontiac	G6 Convertible
e0eae9cf-040c-46da-bf8f-a96ec7fbe07c	Pontiac	G6 Coupe
3c88a044-834a-4ca8-b1c4-829512e96c1b	Pontiac	G8
bd6842c3-e083-4d43-9195-25c557edcc0a	Pontiac	GTO
04b6ce41-525a-44d4-bfe5-2bb2defd7900	Pontiac	Grand Am
f874db28-fb0f-4134-aa7c-d888a2a8a277	Pontiac	Grand Prix
f7479d4c-12cf-4374-ae53-3f37efda3403	Pontiac	Montana
a0718f2b-74a2-4ee5-807c-78934c149cff	Pontiac	Solstice
d82d9a2c-614d-4ca0-b0e6-ad814636d5da	Pontiac	Sunfire Cabrio
58665f7e-9089-4dcb-84ff-ceec24bd9f4d	Pontiac	Sunfire Coupe
b693f0a4-22be-4e68-bbb5-03543be40d8a	Pontiac	Sunfire Sedan
b0c3fe47-7f9c-47fa-9362-159610480cd6	Pontiac	Torrent
42ae43b9-2023-4706-9520-77ec87628e99	Pontiac	Trans Sport
a19449c5-807d-46c9-84d5-b65e27c55e63	Pontiac	Vibe
1dd24f8f-0930-40bf-931d-203355eb7fa2	Porsche	718 Boxster
7e036833-7faf-425f-a19f-a1005e0f297a	Porsche	718 Boxster GTS
57a9243c-4e65-48fc-b82b-d22d1cd29560	Porsche	718 Boxster S
bd1b1fd2-1525-4bd2-a9df-7117b0f6d729	Porsche	718 Cayman
639db7b7-4a76-4fb4-990e-389ef20eb3bd	Porsche	718 Cayman GT4
9a3331d8-592c-469f-bd00-62ba64fbe5bf	Porsche	718 Cayman GTS
e6931f4c-a8c5-4b2b-978c-d9fdd6e4970f	Porsche	718 Cayman S
349b6c27-668f-409e-a69d-cfa0bc3abceb	Porsche	718 Spyder
d71678e0-41ae-4b55-87bf-a42edfb7483f	Porsche	911 Carrera
39263038-2bb5-48cf-8922-9e761dd7a56e	Porsche	911 Carrera 4
3fced502-dd23-4346-90e6-ea5a7e91c6d5	Porsche	911 Carrera 4 Cabriolet
2dda44ce-73b8-42ed-8ac2-06344a40744f	Porsche	911 Carrera 4 GTS
fadfb3bc-e4ac-43cc-a907-59b6e52d9418	Porsche	911 Carrera 4 GTS Cabriolet
30733f47-8c9f-4dd6-9e28-cb9077498dc7	Porsche	911 Carrera 4S
30397495-a3ea-41b4-a5db-07df4400d354	Porsche	911 Carrera 4S Cabriolet
7130f8bd-ca30-47eb-8edf-730f2d2fe969	Porsche	911 Carrera Cabriolet
d22d6d40-9252-4b47-9092-064e12c46b61	Porsche	911 Carrera GTS
13313ee6-664d-4c78-b04d-3b571f6f4dea	Porsche	911 Carrera GTS Cabriolet
c40b6218-0cf3-4712-ac47-0ad0cd2635d9	Porsche	911 Carrera RS
f51d1634-d10a-4ea2-a613-547decfc8669	Porsche	911 Carrera S
c9c62ed0-03b6-41e7-b93d-bee65bfe50ca	Porsche	911 Carrera S Cabriolet
607cbce9-3af2-4d4a-b602-343a411647a4	Porsche	911 Carrera T
97034329-e1d7-4235-8c48-73039eb071ef	Porsche	911 GT2
c5055da6-21da-4120-98d0-42e2e9246e63	Porsche	911 GT2 RS
0e543c5b-86a1-4c76-80c9-47c041ed11e2	Porsche	911 GT3
edb0c1f1-d322-4ae1-bc11-b1f585a2966b	Porsche	911 GT3 RS
2623e6d3-6b34-4f77-ab35-a8f0a1e1dc66	Porsche	911 R
63cf5abd-ad77-4fd8-8114-965470201519	Porsche	911 Sport Classic
4842a6b8-b25d-4a00-87dd-90b29f0e7a8b	Porsche	911 Targa
157d51e4-62ea-4419-a5a8-d199219b37a9	Porsche	911 Targa 4
6d05206b-aa10-4b9e-88a7-020328d60df7	Porsche	911 Targa 4 GTS
28c58e0a-11a1-4fe1-b1ac-019de3f24c13	Porsche	911 Targa 4S
51b84f58-7a8b-4138-887a-ace9e9b55f4d	Porsche	911 Turbo
681d1756-5512-462b-ba8a-1c2975269fd4	Porsche	911 Turbo Cabriolet
7767dc93-c70b-4dbb-92aa-526319609dd6	Porsche	911 Turbo S
0f59a98d-363a-4c38-8d55-b101dc368931	Porsche	911 Turbo S Cabriolet
15215e89-a34a-4ad6-b794-69214dcdc4ff	Porsche	918
5f94f87d-2b1c-4f53-a39e-0666c626c211	Porsche	928
ae585f36-9259-4288-9481-7165df7f9c7c	Porsche	928 GT
9772efd3-aaa4-4206-ad2c-b710f9c572b0	Porsche	928 GTS
1ec52694-1b1b-4632-a5cc-3443d83af5cb	Porsche	928 S
c53347ad-7785-4b16-acc2-2de05e170345	Porsche	944
3b9a3cdd-6b82-40d2-8269-7d30dbf410dd	Porsche	944 S
cb67ec54-5d40-4d92-ba93-84ca42c55bc4	Porsche	944 Turbo
83b620de-8b71-46c4-8a42-5a152d0118c7	Porsche	959
acca7c8c-e20a-4ad6-ac08-a54726c92deb	Porsche	968
ef143ba3-a038-4ba7-a868-f15ff5dd6ab6	Porsche	968 Cabriolet
5af3fedb-035d-4830-ba35-954e3543d75a	Porsche	968 Turbo S
1cb35cd7-b891-42e2-9f16-6fa8f06139ee	Porsche	Boxster
7998bcea-423b-448e-95c0-7d51003f0ee6	Porsche	Boxster S
fca9a5a2-777d-4cba-9fc0-349931b04689	Porsche	Boxster Spyder
a06cb9e0-37c5-4532-b8ed-25c808070835	Porsche	Carrera GT
674986a6-526e-40f5-94cb-9288849fee8d	Porsche	Cayenne
17d0442c-6060-44ce-a0c3-268789be6af6	Porsche	Cayenne Coupe
dd557d29-c773-4687-b479-65be48b0f3b9	Porsche	Cayenne Diesel
4db15f6c-d2f1-45fe-af42-5443022c759e	Porsche	Cayenne Electric
c07c0219-4115-465c-920b-eed530692a01	Porsche	Cayenne GTS
e9cfda3b-4828-4fc8-b2e4-28f29bc8db44	Porsche	Cayenne GTS Coupe
091150b1-6c1b-41b4-b3fe-374cfe56c7e3	Porsche	Cayenne S
b15f249d-333d-4640-96ec-eba73aca7c98	Porsche	Cayenne S Diesel
9c4e77c9-4d19-4077-83ff-61b5de16a359	Porsche	Cayenne S E-Hybrid
d6767b88-091e-4dd5-9f97-ba10d89bc451	Porsche	Cayenne S Electric
161dd06b-e09d-4f58-b773-b5e440567a06	Porsche	Cayenne S Hybrid
3bcf9faa-8b76-45d7-a641-5c807307c363	Porsche	Cayenne S TransSyberia
bed086ca-2f72-40b6-b095-59359f3353dc	Porsche	Cayenne Turbo
51017904-2684-4389-8fa3-32c63a3cf0f0	Porsche	Cayenne Turbo Coupe
ae3d1f47-b15e-434a-811c-06b6d6811a3e	Porsche	Cayenne Turbo E-Hybrid
f8849082-a235-4639-b7a9-e24cf79163c6	Porsche	Cayenne Turbo E-Hybrid Coupe
1f0ac9f9-b3f9-4df1-abe0-a06851b5cf3a	Porsche	Cayenne Turbo S
7bc58268-9e12-4410-8f10-961fcf44cd6f	Porsche	Cayenne Turbo S E-Hybrid
47d533e5-adcc-4577-9996-a23653b28d5d	Porsche	Cayman
aeafe4d2-00e4-497d-8490-3eee9fc7852d	Porsche	Cayman R
0976fa22-5d55-4056-89be-5ebf1d189d1c	Porsche	Cayman S
4c0fde69-2831-433f-b2f8-826919b6dc7e	Porsche	Macan
8512c767-b491-48c8-adf9-a2c56b1f0e29	Porsche	Macan 4 Electric
27104951-3376-4564-97ca-ce6bdadb5ac8	Porsche	Macan GTS
e5f1ce44-b1fc-4eec-82a6-60447e12c30f	Porsche	Macan S
1bd6d193-db0c-45a6-8d64-6fb87d16277c	Porsche	Macan S Diesel
d96e79d0-894b-42dc-b4d7-3d537e8aa61c	Porsche	Macan T
cd19763b-bac3-43be-a55e-fef233dd8be1	Porsche	Macan Turbo
86dbed69-a76a-40d4-9830-b53f3310a1e0	Porsche	Macan Turbo Electric
c6747c30-3537-4b25-9580-c0d52d577287	Porsche	Panamera
2667a3f1-2fc0-4df0-98ba-45747516d35b	Porsche	Panamera 4
11a7ef31-be3c-46f5-a417-b12d5f57bd2d	Porsche	Panamera 4 E-Hybrid
73104091-f1c7-4193-9ffb-3c0b7e23d4aa	Porsche	Panamera 4 E-Hybrid Sport Turismo
cfbe9de7-758f-4075-8087-a7f5e2e4c88a	Porsche	Panamera 4 Sport Turismo
a7b0ad96-205d-4fe1-97dc-5d472038ce08	Porsche	Panamera 4S
0e4ee507-08b2-43b2-86e5-5ed2584a7438	Porsche	Panamera 4S Diesel
653220e2-a39e-491a-8cd7-bfe97db5fbe8	Porsche	Panamera 4S Diesel Sport Turismo
a461e267-500a-4269-b8e7-76214caf32c1	Porsche	Panamera 4S E-Hybrid
09a4ba04-5578-4a72-9b3d-b25c1e7aedc8	Porsche	Panamera 4S Sport Turismo
415f23d2-cf53-43ca-9e21-79afc33c6885	Porsche	Panamera Diesel
eb9ecd70-abf4-4571-99a7-e0075d8b86c8	Porsche	Panamera GTS
10ddc8bd-d1c0-4245-b397-425beb11a968	Porsche	Panamera GTS Sport Turismo
a38e5416-9a28-4baa-8d1c-afec88bad4e8	Porsche	Panamera S
7e28f12e-f9d0-4182-85a7-e73dc42e77fa	Porsche	Panamera S E-Hybrid
424ceff9-a59a-4076-86d7-97e2778375e7	Porsche	Panamera S Hybrid
639004a1-3d76-45ed-8ef5-f22d25067724	Porsche	Panamera Turbo
2df242e5-01a0-4634-a460-cd357de66514	Porsche	Panamera Turbo E Hybrid
9d477a5f-33ec-42e4-9d54-c8f8fed04b3d	Porsche	Panamera Turbo S
fc9b6e7e-ff69-4d42-bf2d-82a80308b0f2	Porsche	Panamera Turbo S E-Hybrid
5348e27e-a75a-417c-9514-e977003c9b11	Porsche	Panamera Turbo S E-Hybrid Sport Turismo
1c29493c-8a9f-45fb-9448-7b3ce826feff	Porsche	Panamera Turbo S Sport Turismo
1df8c4b3-9fa5-4aad-b84c-b00626647e63	Porsche	Panamera Turbo Sport Turismo
63144f5a-1ff4-452a-9cef-2c62d488b9a6	Porsche	Speedster
d904fbe9-9227-4a27-9477-b778b46e1ba4	Porsche	Taycan
13dc6f45-0f12-49f2-a77d-a15ecf697cc0	Porsche	Taycan Cross Turismo
02400f40-71c1-46ea-83fb-9959ff5f727d	Porsche	Taycan GTS
c7c89d42-2369-42dd-a746-3f62861a06d5	Porsche	Taycan GTS Sport Turismo
2765ad15-20dc-4308-b5d0-f57905a54a69	Porsche	Taycan Sport Turismo
cb4045a5-f3ff-48eb-b508-cedc886c12ac	Porsche	Taycan Turbo
7b1254ae-f84a-45a5-abfd-d93317a9a044	Proton	Arena / Jumbuck
8d944d8d-f4fd-45be-b358-e5f7922461a1	Proton	Ertiga
344d8c0c-26d2-4586-841a-65bbddfa891b	Proton	Exora
c7dd9fed-f99b-42e6-9fbe-2756b2989d91	Proton	Gen-2
8b74ed22-48ac-467d-b363-0bae6e9b2700	Proton	Inspira
28af9473-3f36-457e-bf3e-dcebe6271bb8	Proton	Iriz
9943c6cb-f0f1-47bd-8e6b-83a5c79c523c	Proton	Perdana
b27e4845-8572-46d0-99f2-5475e22e5238	Proton	Persona
12920904-a265-43fb-aa53-4464f4a5e93f	Proton	Preve
c3f78b8b-ba00-4a37-8904-7988205b5e0e	Proton	Saga
022f6294-d646-4d66-9de9-5b43a739f5a0	Proton	Satria Neo
08aa6bcd-53f8-4f5c-9fff-e1a13790bebd	Proton	Savvy
85ce647b-4de2-4983-b3c5-a1153816533d	Proton	Suprima S
e3fd14c7-93a8-493e-bfb3-481110b81714	Proton	Waja / Impian
ed5eca42-0dca-4e22-a08f-d3d39ca015c5	Qoros	3
45d71e8e-21d4-4bc0-9923-9c8c7deda88b	Qoros	5
3e354f9f-6d02-4dc6-8dc6-cd710e01078e	Renault	11
9f52bf9d-6799-4eed-9167-0cee2428f2ac	Renault	12
39fc78db-9bf0-4935-8f15-e4deb29bd9eb	Renault	14
e9214ad4-29a6-4876-82e1-d060276a490a	Renault	16
dbd3ae39-c711-4f64-9b29-3e3b21561804	Renault	18
f58c1ef7-6eda-4de0-8bda-962a4ec77b31	Renault	19
aa6c1774-84b6-464f-98cc-70d6fbe5f71f	Renault	20
c5291941-3b06-4773-90ba-bfa3a3acaf95	Renault	21
66bfc0ed-0682-4ff5-aa08-2495206e02e3	Renault	25
9002bdb1-452b-4442-8560-320ba6ec142a	Renault	30
a1d4e8d1-e248-4615-81b8-1ff6a6cc3a10	Renault	4 CV
a81aee6a-19d4-41e2-9ff8-89df65d2b2f0	Renault	4 E-Tech
f2c16627-742d-4d57-a664-f8a25407eefa	Renault	5
39e70df5-424c-4725-a14e-c803862213bf	Renault	5 E-Tech
f4e5cc25-746f-483f-b549-5011cfae004c	Renault	8
00248f22-68ef-454c-96fe-1206a01e132e	Renault	9
3612ddc9-e6da-47ba-9815-345ed5e4c8ec	Renault	Alaskan
9efed616-00fc-48af-b663-ff957a6dd710	Renault	Alpine
00219dfb-1146-4008-b541-350895eb0b56	Renault	Arkana
bd0287bf-c9ad-44a3-a5f6-77b54a41a15e	Renault	Austral
1b119e90-dcb1-4351-98f7-c634832ba505	Renault	Avantime
317b6bf6-79b2-4d29-9908-4029e47f113e	Renault	Boreal
984d01f3-944d-43ad-8ae4-8773c9aa0668	Renault	Captur
06505721-f900-49d7-bce3-bb9c8153bdd2	Renault	City K-ZE
6ae4ff09-e361-4d42-a6b9-8fcd8e4ca901	Renault	Clio 3 Doors
318159bc-ce99-40c3-b910-63119647ea81	Renault	Clio 5 Doors
ec93ad27-f3d3-419e-9a77-05cc344baf3e	Renault	Clio Estate
38ca66d4-8e25-43fa-a424-cccf5ee132ee	Renault	Clio RS
a30d810b-df7b-4b4a-9f67-6df88c335d12	Renault	Clio Symbol / Thalia
1a93f57a-4f4e-4fe2-952a-25bb4d64fad8	Renault	Duster OROCH
ec3f8219-8535-434c-8034-390177326ce3	Renault	Espace
d8e17ef5-441b-46b7-a0de-3ee514bd094b	Renault	Filante
c6c1f1d0-e9c4-4fce-a8a2-d91ef029acaa	Renault	Fluence
fa2352f3-04cb-49d6-89f8-e5f2ccb407dd	Renault	Fuego
0222e2ce-2398-49ed-8039-6e9a60e5a501	Renault	Grand Kangoo
a5d1d0e2-e8bf-45b0-bce6-b4ebd7c3a7db	Renault	Grand Modus
1f20142d-2e0f-41da-aea3-5e63e1f82e8c	Renault	Grand Scenic
9225f9d8-671b-4b67-abe2-1af8160a34a0	Renault	Kadjar
a276b724-f908-418a-b1fb-d77a3a8aa138	Renault	Kangoo
4e5bbd2a-bdd2-4561-8545-746b6d1bfaff	Renault	Kardian
11e5b24d-6498-44b7-8c1e-6def13060143	Renault	Kiger
69fd9a3e-b6fa-48d7-bc2b-9eb9ee1482ba	Renault	Koleos
439185f3-a79d-4f54-a9b8-42eab87a211b	Renault	Kwid
16acbb3a-10bc-4e78-9015-6fc87c1006d2	Renault	Laguna
ddaf1020-ef36-4b39-a1b2-839a661beeda	Renault	Laguna Coupe
50b2ee27-3754-4191-902b-ad537aa20754	Renault	Laguna Estate
ea2d90bc-0d27-497b-a7ed-307c766096de	Renault	Latitude
83412da2-101d-4c2f-8e26-6134171433d3	Renault	Logan
047b319e-3954-4bb3-b2a9-4fa998bf839f	Renault	Megane 5 Doors
a7d9095e-38d0-401a-9d22-72cdb85705a1	Renault	Megane Cabriolet
576fdb6e-208d-43e5-b3e9-0d24b61c277f	Renault	Megane Coupe
a21d9f7a-d02e-45d0-9719-95cca0be29dd	Renault	Megane E-Tech
1a03b4ba-e358-4d70-a83c-20a4fce462ed	Renault	Megane Estate
05c99da7-8145-4b30-98b1-74bf38007c47	Renault	Megane Sedan
67c60123-61bf-4c1e-b3ef-c700ba1d27a5	Renault	Modus
c0324b8d-6aa4-4c01-9490-1b7cfd393372	Renault	Pulse
4eee6128-204f-4696-af36-02ed13f8d89f	Renault	Rafale
73b9fc5e-e0a2-4d94-8181-ecc5215f0f04	Renault	SCENIC XMOD
efc8a75f-8c0b-4571-800e-af3b780ac3b4	Renault	Safrane
b3c07f86-f8b1-4bf9-a114-53c3e03cde40	Renault	Sandero
e92c72ea-21fb-44ce-bea5-b7140a286e80	Renault	Sandero GT
13165ac9-7c1f-4e80-b4b6-4dacd4fb253c	Renault	Scala
60b9ed95-e831-4030-9ef3-b4e8b06aa7bf	Renault	Scenic
502989c9-aebb-4ce6-b6f1-eadb3f742531	Renault	Spider
6aaa15ab-697a-419e-a09e-e3cad030ba89	Renault	Symbioz
208b9214-68eb-4a21-8429-52599595b9ca	Renault	Talisman
f0e9467a-8bcb-4e96-b703-c4dc3557f7e1	Renault	Talisman Estate
8af7d09a-e7ae-4f83-9413-12f0d99dd121	Renault	Triber
bea9456f-96dd-43c6-932e-4bcf28dbb6c1	Renault	Twingo
43cf432f-f303-4205-85e6-de02eb41cf85	Renault	Twingo RS
adf0cc59-4b1e-4d21-b3d3-ba88437bf8bb	Renault	Twizy
bca99313-ff9e-4bf8-ad6f-141b4021f063	Renault	Vel Satis
4359f62d-9742-432e-8eca-a56de026b091	Renault	Wind
f2c6cb99-221d-465a-8ba9-00c0b45ad53a	Renault	ZOE
4a7288bd-baba-4c78-87e1-d8380d266b76	Rimac	Nevera
b2fa7ff3-2361-45bc-ac52-6a3b4604e9be	Rivian	R1S
4ae15073-caa3-4d96-ac39-1cd197de013a	Rivian	R1T
64b94f44-8c15-4be4-9168-73fafbad6318	Rivian	R2
6aa02c59-9988-49f6-93a2-0fe9eeffffb9	Rivian	R3
f1e0de03-06c8-48d6-9e60-686f749ce37b	Rivian	R3X
b2993145-aab1-43ec-bf8e-14b83c538ae7	Rolls-Royce	Arcadia
4a8171e4-250a-441a-baa9-852508565f25	Rolls-Royce	Camargue
3a6b888b-9a3f-4a3c-8c8e-8a4d5be4e0b0	Rolls-Royce	Corniche
edf4339a-a28a-455c-b9c8-1510d13534c7	Rolls-Royce	Cullinan
270bd29e-ef73-49fa-a73e-2dbde8fa8abe	Rolls-Royce	Dawn
eef22846-62b5-4081-ba3e-e04d25884949	Rolls-Royce	Flying Spur
fbb3d12e-1148-4b0a-926e-724960cab1a9	Rolls-Royce	Ghost
3ce4fe4d-daee-4c4f-a3f2-8fb6f0691f64	Rolls-Royce	La Rose Noire
140a7a06-9090-4deb-87e9-521adc36a2d2	Rolls-Royce	Park Ward
aa32f662-de0c-4442-9908-eedb8c375155	Rolls-Royce	Phantom
5219da28-8580-4489-af22-c1395c0db716	Rolls-Royce	Silver Dawn
5dfaa03f-6077-4bb5-8e87-1ad3a408141c	Rolls-Royce	Silver Seraph
7a9375f4-7cfd-4d8e-b7a9-dab2336c9ee7	Rolls-Royce	Silver Shadow
dc809941-6ff9-4a5c-95b9-8d1a6e54c69f	Rolls-Royce	Silver Shadow Coupe
59f8eda3-be93-4d0a-b005-15b19054e5cc	Rolls-Royce	Silver Spirit
a3bc3fc9-7908-4c42-bf45-d9642b399021	Rolls-Royce	Silver Spur
69aeabd7-3e28-45f1-898c-52497e9d1364	Rolls-Royce	Spectre
08e335a6-b6b0-4e1d-883e-23d16c7f54f5	Rolls-Royce	Wraith
7c2d76fa-31e4-416c-84f4-de3efa70596a	Rover	100 Cabrio
f35c9173-3fd0-4400-836b-00b3bfa8f7aa	SEAT	Alhambra
c8fe5617-085a-4bca-9110-4d6600293855	SEAT	Altea
4f96f833-4593-4ba4-9c8c-41d4bf387744	SEAT	Arona
9b9d3173-01eb-4708-b156-799637385684	SEAT	Arosa
89959c52-7e89-41c5-85f1-f2a76ca0577a	SEAT	Ateca
c5c26175-9307-4f6d-9421-17daa8c02aff	SEAT	Cordoba
53dc3c97-91e8-4b65-8d96-2444da0b6a03	SEAT	Cordoba Cupra
d5bd4c8e-9c61-4666-907f-ae214b10b0b2	SEAT	Cordoba SX
60a3cf66-0243-45b7-91bb-320210ef28ea	SEAT	Cordoba Vario
b2c3704e-e04f-4244-9dba-b1902535adc4	SEAT	Exeo
785006a5-569e-4d20-b814-4f4a34026c2b	SEAT	Exeo ST
c0ca5467-6a91-44fa-8e6e-c412d6a567f7	SEAT	Ibiza
f0c8a6b8-8390-4999-a527-d8d15ed595bc	SEAT	Ibiza 3 Doors
ce6910e3-7be1-4edb-a8f3-7ff56f1eeffb	SEAT	Ibiza 5 Doors
1507a918-99e5-4f19-897c-60feb5e432e1	SEAT	Ibiza Bocanegra
df811a29-145e-4076-8bcf-68afa6d73daa	SEAT	Ibiza Cupra
67085cea-b387-4251-aaaa-b7bda5b5c589	SEAT	Ibiza FR
d1575c49-a594-44ad-b35c-006acec98380	SEAT	Ibiza ST
873af08e-bd5c-402c-97e0-ae3060f1ce6f	SEAT	Leon
3b90c5ec-8bf1-47ef-ad91-32d827f1fb54	SEAT	Leon Cupra
069e5511-08df-4b5c-9882-c27f78f1ece3	SEAT	Leon Cupra R
3bb2c0c2-37fb-4c5a-82cf-915fe152945d	SEAT	Leon FR / Topsport
e097c1ec-2e4a-48e2-8c4c-4633f1433000	SEAT	Leon SC
af19c994-c7b6-45b6-a933-d0d342acd0f7	SEAT	Leon ST Cupra
ad65c7bc-2d30-401a-904b-8db859d8dc23	SEAT	Leon ST Estate
f0287cd7-d34d-48b7-bc3a-d42a2a65565f	SEAT	Leon X-Perience
3919852b-8fef-4a4f-b48e-f5fdff77b420	SEAT	Malaga
7ae2c31a-b894-455b-94fc-e7b04de5d088	SEAT	Marbella
ebf00b99-f09f-42bf-8834-fd903e0a3638	SEAT	Mii
a949f3db-37d8-4daf-bfa1-d4ca0414da47	SEAT	Tarraco
8471fd6d-d8ec-490a-9b48-88378b32e6e4	SEAT	Toledo
f69d3a48-9a8d-46e2-a0ab-88d368b82c96	Saab	9-2X
ebc201ba-6708-454e-82ff-7ab606bd2543	Saab	9-3 Aero
51944810-6502-469d-924c-53c09a73a73b	Saab	9-3 Convertible
8c7c08a3-17ce-41f6-8d6a-19e7fef92504	Saab	9-3 Coupe
701f0478-eb43-45eb-944c-9b681a9168e1	Saab	9-3 Sport Sedan
e8811203-1d8e-4714-88cc-a7c44da78037	Saab	9-3 SportCombi
8f008906-1ac9-4de9-b58f-442fb4eb7f50	Saab	9-3X
dad70119-5d73-4df1-80ba-ccd1e5dd81ae	Saab	9-5
d6930b80-ec39-4547-9f98-45076c4c45fa	Saab	9-5 SportCombi
c77de2ef-185e-4253-b85d-279a8108302f	Saab	9-7X
d9c2b7a3-d6fa-4339-9dd9-a34e69ab9e1b	Saab	90
58200ce2-4500-445d-9d89-94e65d16356e	Saab	900
2b50752d-896a-4a20-961d-2f0170bbb5a5	Saab	900 Cabrio
d0087e3b-1765-4c69-aa55-357edcdfaf5b	Saab	9000
f21dc895-16ff-496f-85a9-6fd013a6e2dc	Saleen	S7
d71dc955-fdb8-49fa-8369-57b5c9e9d669	Saleen	S7TT
f7624b0f-d332-4863-a046-561023827feb	Samsung	SM 3
4aa7ce12-4d15-4092-af32-2e9c488a4213	Samsung	SM 5
c5ec5702-180e-4282-bbe4-3d39ceabebef	Samsung	SM 7
9da45bbe-4cd4-4d2c-b5c9-5551f9cd417a	Santana	300 - 350
5da43eca-530d-4e2f-ad0a-30de666ea547	Santana	PS 10 Pickup
1f1ea0fc-8f2b-4539-a4ef-8df1eabc28fb	Santana	PS 10 Plus
d7b2c1a0-ec5c-4ae8-a64e-913fba9b1608	Santana	PS 10 Shortline
744ba80f-798b-435c-9a66-dbe63ac67ab4	Saturn	Astra
084d5a08-fd16-4bbc-b494-59effe5a4c37	Saturn	Aura
97b889f2-7b60-463b-b967-5dcb9f75ab83	Saturn	Ion Quad Coupe
fe83d2e6-42ed-47e2-baee-c80fa0c848bc	Saturn	Ion Sedan
6e329443-6f2b-4d8b-9546-ad3b8435a872	Saturn	Outlook
1d0ab092-e970-4597-b546-8d896cd35dd7	Saturn	Relay
e89295d2-91f8-466e-88ef-84b682676e4a	Saturn	Sky
4095ce53-f6a5-4961-8614-80fa1957718d	Saturn	Vue
7ecca7cc-db37-4ec1-a7af-fa93ab15a40e	Scion	FR-S
c3ef780c-b602-4cdc-bd2b-b313d3e34edc	Scion	iA
cad23870-d3e6-45e4-b712-35c19ce25d70	Scion	iM
ed6bc629-9526-4780-b0da-8453fbe2183e	Scion	iQ
2a3200ee-9a8e-4103-98cf-73401c67b09e	Scion	tC
8bc8a733-28f0-4dce-8dc2-3cd03971dad1	Scion	xA
a5a1c04b-ab72-4094-bd33-62efa41d8d02	Scion	xB
529cf087-8819-4cb6-a672-7b2d89b5196c	Scion	xD
a09615cd-cd64-486f-bd23-47fed91e0946	Scout Motors	Terra
1dbb4342-d912-46b8-8a02-3d3eed6bf176	Scout Motors	Traveler
b9c6f400-7e46-4d39-a667-4f3c2e0b2c4b	Smart	1
1100a46a-b294-4f6c-8c01-6292499828d4	Smart	3
e38ce66f-53fe-49db-92ab-013009e1cea7	Smart	5
8e13b482-7ed0-4a40-afc1-1b29ca911563	Smart	Electric Drive
a0fddfc4-a111-44c7-834a-2bf314974344	Smart	Roadster
0466d1c5-e091-48f1-bcf4-ea918e8e43fb	Smart	Roadster Brabus
cf7f4164-b1be-40c4-ae3a-b2084d7ac1d5	Smart	Roadster Coupe
ed15f064-59de-45a8-af8c-494ec81fdcfa	Smart	Roadster Coupe Brabus
10f2afd9-d5bd-49ed-bb4f-856ce5bd2f13	Smart	crossblade
3f473643-37d0-43e6-b84c-2f60d6f3be4a	Smart	forfour
12c7dfe1-8d82-446f-bab2-d9059348a7ab	Smart	forfour Brabus
ae017e26-f45a-45d9-ab84-f7f3664f764c	Smart	fortwo
214bfdf1-58a0-4984-b797-3f835238d8de	Smart	fortwo Brabus
dbc821be-ec75-4de3-b5d5-c32365789919	Smart	fortwo Cabrio
0d805834-f0da-4106-933e-d3efe5b82b52	Smart	fortwo Cabrio Brabus
37513565-c3bb-4305-8577-fb588480523a	Spyker	C12 LaTurbie
99d2731c-a863-4038-ac2d-4bb31e0fd889	Spyker	C8 Double 12 S
416acae7-75ba-4a00-8a26-fbdb3d1b85af	Spyker	C8 Laviolette
0e6f9937-ffdd-4f7b-801d-8d9c4cb422eb	Spyker	C8 Preliator
9c1bd87d-87eb-4ece-9d19-fd6276169e89	Spyker	C8 Spyder
0553e5d7-b733-4bc9-8203-e387f3745dd9	SsangYong	Actyon
59fe1c4f-cdd4-4d73-a082-8f29f138f8fa	SsangYong	Chairman
92521137-5503-4802-b8e8-0b89aea018d2	SsangYong	Korando
6dc30f54-b50e-401a-8186-77311e08e9d9	SsangYong	Kyron
00c4bbc3-f509-4bdc-b2c0-f827dbb21c29	SsangYong	Musso
c03a7595-f0a5-42bb-b613-3a60b07b8086	SsangYong	Musso Sports
7b56cf6a-d451-4e05-bb1b-dc0e0525d889	SsangYong	Rexton
3a26893e-0708-4654-8cf2-b3b15c66c86b	SsangYong	Rodius
711643ad-75ee-4516-bd5e-0d9b4be945bd	SsangYong	Tivoli
8acbfb49-7777-4c11-a00f-bcca1e994ce0	SsangYong	Tivoli XLV
7baa0d51-27f6-4105-8d82-942425095cf4	Subaru	360
62b4cc50-41a2-4ccc-b080-81203e3a8101	Subaru	Ascent
325f5472-cbf1-439a-a974-899704a54ce3	Subaru	BRZ
140164dd-1fd5-406c-adb8-f4b40eeb35ef	Subaru	Baja
6509210f-11ed-48dc-9787-c82923570a5e	Subaru	Crosstrek
78b8bb30-0a96-4bf7-a0d5-72f621d8613d	Subaru	Exiga Liberty
7f8e25e8-ad29-44bd-8aa0-b316b33786ac	Subaru	Forester
865ec33c-7d08-4335-b1d7-c92d5f591aae	Subaru	Getaway
912c380c-018e-446b-aed2-b3d92552e64a	Subaru	Impreza
0a8d32dd-b669-495a-82d1-1a1de7470afe	Subaru	Impreza WRX STi
36be8207-fe0e-42e8-8e77-a77c39883411	Subaru	Justy
d8b902fa-966b-4bf2-840e-33210426c08d	Subaru	Legacy
64e4a67d-67b1-453f-9c5c-cb6fdfce581f	Subaru	Legacy Wagon
2ed32b83-3e2d-43e3-95b0-588d674276b0	Subaru	Levorg
7058bbe4-48c8-467a-9fe1-7e2f32784985	Subaru	Liberty
b38fa01e-c631-4c27-afb1-6883de7403fe	Subaru	Mini Jumbo
f588ad62-27fb-49f6-9217-81e34f0aeedf	Subaru	Outback
5b214d66-ca91-4810-a54a-55c98dc7be6f	Subaru	Outback Wilderness
f6c287fe-5170-4246-a8cf-bd90cb2c06ea	Subaru	R1
cbe9a571-fd08-47c1-8093-ef7ded347e59	Subaru	R2
f957fad4-da31-4e1e-953f-d3d7d9cbb549	Subaru	SVX
46259a3c-d0ae-44b5-888d-7c3b7522bd5d	Subaru	Solterra
c07f9efd-b06c-47b5-8535-e2050e4e7468	Subaru	Stella
3aa7c82b-2c93-4a42-bd5f-3d5569159e92	Subaru	Trezia
f7cf99c3-a183-43cd-b664-aa6ddfb7c2a3	Subaru	Tribeca
5bdfe083-3cf3-4089-a9af-e5f8725de9b6	Subaru	Uncharted
eac825de-f6ca-4cef-b491-57c8dc70e472	Subaru	Vivio
ccdc9d60-05b4-49b3-bb85-637135bcb727	Subaru	WRX
60112870-cdb9-421c-8bb4-eaff810698ee	Subaru	XV
48253363-f6e8-4ae9-b4a0-c6842108dbb3	Suzuki	APV
90fc35ef-d536-4979-9c8c-4c09a8085324	Suzuki	Across
3dcf946c-6c6b-43a0-ae49-ac1eaf6a8581	Suzuki	Aerio / Liana Hatchback
bb6d8b1a-7a57-4829-a82e-a4e50973d3d0	Suzuki	Aerio / Liana Sedan
62206db6-9af5-4ac1-81a4-9b4ac7da8129	Suzuki	Alto
cc665fb8-4d1e-42e8-b512-8244a2bc1797	Suzuki	Baleno
a424192e-cfb8-4630-95ae-cadbad6d018a	Suzuki	Equator
9381d546-4abf-435c-998f-66c93cc75bd7	Suzuki	Ertiga
a76e4a29-9be4-448b-9031-e2c7c872ca54	Suzuki	Escudo / Grand Vitara 3 Doors
e7f4bfa0-6bc8-4ac2-93de-5b1dff7e84cd	Suzuki	Escudo / Grand Vitara 5 Doors
ebfb1c3b-d13a-42c4-9a39-d59a606b49e2	Suzuki	Escudo / Vitara 3 Doors
a39209eb-d564-484a-a9b7-bf50bdff725f	Suzuki	Forenza Sedan
9df40402-7e83-4c51-939d-2c328019ddeb	Suzuki	Forenza Wagon
374c3b43-3544-4de2-acfa-361e408c19b2	Suzuki	Ignis
373becf7-3fec-4faa-a456-3e4206bb2680	Suzuki	Jimny
ab89cbd9-a82e-4367-a732-83dafc358057	Suzuki	Kizashi
41057697-f082-4c1f-9113-1111043055b7	Suzuki	Reno
5bc8ffce-84a8-44b3-a7a5-d43796af6f07	Suzuki	S-Cross
475e199f-fb1d-447c-97bb-0b3df32cd23c	Suzuki	SX4 Crossover
8d3ffabc-119c-4e15-99f5-8d027665caea	Suzuki	SX4 Sedan
8859f65f-6d86-4c9d-a91d-637e80ba9918	Suzuki	Splash
912247a8-bfe4-438c-93a4-41956370b113	Suzuki	Swace
1515087e-3b62-4ef9-8ff2-1bd05fd1702a	Suzuki	Swift 3 Doors
aabc718c-a2b7-4cf8-8f68-1bbb6cfc2ebb	Suzuki	Swift 5 Doors
851e3b5a-50ae-41d0-bea1-f1518491051b	Suzuki	Swift Cabriolet
d84836e5-500e-4073-b819-fb981921225c	Suzuki	Swift Sedan
e01c65d4-3bec-410a-bc17-ee9f569503e0	Suzuki	Verona
84b1ac46-609d-4d67-94e2-8b1654ee295e	Suzuki	Vitara
8d13e698-3447-4101-b170-0ccaadf04842	Suzuki	Wagon R
3ac556fc-c923-4f35-aee4-495bc845b33f	Suzuki	X90
b7f1310d-3580-4540-8dc6-c5fe116901c3	Suzuki	XL7
27fe4032-6712-4d7b-a014-a00071a57442	TVR	"Wedge Series"
d232e108-49a9-4c00-a2b2-45afae9911df	TVR	Cerbera
80df6eb1-f873-47ea-9d23-dac11773b756	TVR	Chimaera
9633d96c-879d-4d62-b8ee-7fb6da3983b1	TVR	Griffith
96ef75ab-5cb0-4640-b457-c56f280c2a3f	TVR	Sagaris
8036b51b-6fa2-4c95-b5d9-fa76c71dd4f8	TVR	T350
18412b73-ccb2-42ae-9045-8b3f05c848c3	TVR	Tamora
8e828134-d64d-452c-aff6-1b48c0d86d92	TVR	Tuscan
50313036-4f96-41a6-ad0d-ed35b172a5d6	TVR	Tuscan R/ T 440R/T 400R/ Typhon
6d05e6b5-788a-49f5-88e4-1657ab2ef174	TVR	Tuscan S
ecd338e4-d37b-4834-9ebd-c302873651c0	TVR	Tuscan S Convertible
1b1abb3a-ebde-4595-a01a-5eb1b3ce3a63	Tata Motors	Altroz
f3a55f54-2351-4cda-930f-2093bb61e730	Tata Motors	Aria
f7c87096-c886-4544-8e46-413df907477b	Tata Motors	Harrier
c35c12da-8e48-47c2-8f1a-7594bdfa3f69	Tata Motors	Indica
53859f28-dbbf-4893-84e8-8e34679312ff	Tata Motors	Indigo
590e3339-3044-413a-8449-55fed36168cf	Tata Motors	Indigo SW
1a4cf1fe-afb2-4118-8f00-980efbe5bb9c	Tata Motors	Nano
b94d84c2-864d-4d71-8e3e-aa7821ad1b81	Tata Motors	Nexon
c4caf230-5b12-4383-aa57-3fd2426b9fb4	Tata Motors	Safari
59f5ddbf-d87e-4bc4-8354-c08dd0aab935	Tata Motors	Sumo
c416772e-d760-41f4-b15d-a7ecdea8a504	Tata Motors	Telcoline Double Cab
fdd6ffe2-f3d4-4dce-b40f-2c7cb3d651ee	Tesla	Cybertruck
e1eb1938-1a36-4260-8d14-d85c36a5ca49	Tesla	Model 3
e74cf270-00b3-4656-b473-ed8a63791a58	Tesla	Model S
162d0d83-be12-471a-ba5c-13660d85c21b	Tesla	Model X
a3e50f69-f90e-47bc-a17e-126966b8663d	Tesla	Model Y
19586656-1499-41bc-8989-468ad8e64444	Tesla	Roadster
4458cc69-d1e3-46d6-a33a-c8b8cc9d1a0f	Togg	T10F
e0ce373a-56de-4919-b129-bad5b2965e77	Togg	T10X
ebce25b6-90b6-4512-9188-efadf6ed8875	Toyota	4Runner
2c063233-3dd7-4cfa-9409-e5c8ca9a636f	Toyota	Agya
de6be3cf-4aaf-447c-8e64-1273ad1d72f9	Toyota	Alphard
76cd5555-c81c-421d-a29c-fc45550101a6	Toyota	Aqua
1c09d7f1-f566-4d4e-92ac-bd513346dd99	Toyota	Aurion
0c37332b-6128-43f9-9d65-386b21c35896	Toyota	Auris 3 Doors
7abba784-c8a1-4ef9-8e42-798b597252a1	Toyota	Auris 5 Doors
9e119d09-17b3-44fc-8565-9d5fe99e16fa	Toyota	Auris Touring
9f3ff957-757c-4921-947d-db59f48ba4d4	Toyota	Avalon
4775a257-02a8-4a22-9f7a-2401bdd27805	Toyota	Avensis
3528c5c4-6fd7-4994-a750-df7284b64210	Toyota	Avensis Liftback
9ad085e0-0fe6-410b-8763-9e9ecc072691	Toyota	Avensis Verso
f02c0cad-3d70-470e-9bdc-ca1c3e8a3617	Toyota	Avensis Wagon
c9deed4e-5343-48ed-98b2-2507be087426	Toyota	Aygo 3 Doors
632deb54-b213-46a6-8dd9-bfa31f58942a	Toyota	Aygo 5 Doors
440a9bec-2220-4e61-913f-3f9a7f2c5576	Toyota	Aygo X
be581c51-1e04-4774-8680-da8c29f64b39	Toyota	C-HR
c8d07e05-697a-4fe9-ac83-9717bcea256e	Toyota	Camry
8eda02bf-bbb0-4d20-ba89-e5e710a3629f	Toyota	Celica
cdaf677c-6ffc-43d4-a95e-17d0531b9576	Toyota	Celica Convertible
a0a59093-0078-44a8-b99b-4f0969bb4f06	Toyota	Century SUV
e87d612f-398f-461e-b13f-f292d352b43e	Toyota	Century Sedan
2c535dd6-64aa-4123-bf6c-fbdb1a5a355b	Toyota	Corolla
5838404b-91bc-49a6-822f-ea3999d3b2f4	Toyota	Corolla (US)
2ded708d-b2bd-4660-9535-1486d7cbf4a9	Toyota	Corolla 3 Doors
63e5e95d-2f7f-4310-83df-9f633eaaceb2	Toyota	Corolla 5 Doors
628798c4-641d-451d-9549-fa8d70fe28f5	Toyota	Corolla Cross
67851a77-7f7f-4f8d-9a3b-eb71b7ddfcfe	Toyota	Corolla Cross (US)
e51c16d2-b570-412a-a3c5-7c6d1afc081a	Toyota	Corolla Liftback
ad3ba4aa-3fc7-48c3-9a0c-55aeac4c5a90	Toyota	Corolla Sedan
9be7989f-529f-4c45-a78a-3b6dfdaa0a1a	Toyota	Corolla Touring
da1d8785-71f0-427a-bfa3-79c941b171a9	Toyota	Corolla Verso
c0a17639-bd73-4a50-911d-e73c2d12fd43	Toyota	Corolla Wagon
eb7623f3-69c9-4f9b-87da-62e82355ae66	Toyota	Crown
be719617-9ef1-4b96-9770-7a86ef30bdf2	Toyota	Crown Estate
8eaea1fe-489e-466e-b7e2-5a12359a904c	Toyota	Crown SUV
c6c30cd7-1bff-489b-bee7-dbb57dee5668	Toyota	Etios
7eda0d9f-6bbd-44a0-8720-ad6dbbd443ec	Toyota	Etios Liva
fdb3a147-df57-47a5-9b3c-2fdb9d44ee98	Toyota	FJ Cruiser
e042b488-899d-41ab-91ce-80fb994fcee4	Toyota	Fortuner
a139d4c2-9f31-4632-95e6-bef07e7e82ee	Toyota	GR 86
45ece258-efc6-41b1-84c1-6119cc53de06	Toyota	GR Corolla
1de9e9a4-7d4f-4960-80bd-99dce043b9e1	Toyota	GR Supra
6c28d9d0-747e-49fe-9a8d-e273eb7eecf4	Toyota	GR Yaris
8e32b693-294d-4b03-b082-4d0b0a740b0c	Toyota	GT 86
f1598a97-19f8-4572-a1cd-c9e09068e0c2	Toyota	Grand Highlander
5c5eaa77-5829-4e1d-9775-c020d50692a7	Toyota	Harrier
9a868360-df2d-4d99-ad3b-90e468edc034	Toyota	Highlander / Kluger
1144fe65-6865-4db4-9691-fc5259f5b751	Toyota	Hilux
5e087a63-548e-4fdd-9e71-2f973bfd1de9	Toyota	Hilux Double Cab
31ef7053-c7cb-4ece-94c9-205370b5d05d	Toyota	Hilux Extra Cab
01c05ca4-1e19-4d5b-a0d5-0e974d78a708	Toyota	Hilux Single Cab
e8e00d2d-b4f2-496d-a183-bc2fff2a0b6e	Toyota	Innova
31a3de50-dace-489f-8f93-df2e6e823eab	Toyota	Land Cruiser / Prado
e3515a30-93de-4d50-92f0-903fe4443d49	Toyota	Land Cruiser V8 and predecessors
8f3e189a-c0f3-4af3-b08c-357536e37de9	Toyota	MR2
26bc9122-d71d-45f7-a943-9b6b9d516a91	Toyota	MR2 Cabriolet
706a1bbb-e0ba-4709-9d7f-5f9ba3dbfa46	Toyota	Matrix
8ee6ed00-82a9-4006-84a3-dd1504d8e50c	Toyota	Mirai
9fcdff78-d839-49b5-9a25-2bd9b70e235e	Toyota	Paseo
2bbabdd8-31c7-4c11-8bdc-30e216029ca3	Toyota	Picnic
84c50c1c-eb19-47a4-8928-643d21dc21e5	Toyota	Previa / Estima
a36984b1-feb5-431b-aca5-d33ef023120f	Toyota	Prius
38fbf0bf-f24d-4a6a-919c-26d0fe21935a	Toyota	Prius C (Aqua)
6b824118-e74d-42a4-b54c-19ddf664e445	Toyota	Prius Prime
d0792586-188d-4823-9fc1-6f1bc30fc6ba	Toyota	Prius v/Prius+
c8053cb5-6867-411f-bc4f-d5611500f82d	Toyota	RAV4 3 Doors
71a6d24d-92a9-4df2-9fee-f504dfb121f7	Toyota	RAV4 5 Doors
9e32dd46-ef9e-4979-a374-acd68935aeb4	Toyota	Rukus
aa47c62e-eb3c-4e92-8068-aec1d9c637e6	Toyota	Sequoia
703df49b-82bf-4f81-a87f-69a30eee78d1	Toyota	Sienna
d8c27a56-d042-4ced-9c08-0f83ea779f7d	Toyota	Solara Convertible
8f904bed-f258-4c5b-919c-074ea8e592e4	Toyota	Starlet 3 Doors
cab130aa-a13b-4cd4-af4d-b740cf2293b6	Toyota	Starlet 5 Doors
c2cef6a8-5d41-4fcc-813f-e0447be6b78a	Toyota	Supra
1bf09f71-0b6a-4aef-b5a3-af3c9b2e0ac6	Toyota	Tacoma
c6393cf9-fd52-434d-9166-614b33d8cb59	Toyota	Tundra
585da1a3-4b49-4091-bfa8-213e98a76b59	Toyota	Urban Cruiser
f1448326-974e-4969-a489-1598f8d47e05	Toyota	VERSO-S
918653d9-449b-4966-b854-40019986a768	Toyota	Venza
2f9a6359-82fa-413b-94f1-ac8d9faa79a8	Toyota	Verso
97fa101a-d822-477d-8990-69cf92174a3d	Toyota	Vios
98301c2a-b098-43ac-8259-0d004f5d0423	Toyota	Yaris 3 Doors
28460f6c-5bff-4e0e-aa13-f5a4089304e7	Toyota	Yaris 5 Doors
fb40b541-9080-495b-aed2-72f6032e49d4	Toyota	Yaris Cross
00110b91-f463-4edc-a745-b8b711c0e87f	Toyota	Yaris Hatchback
dacde30a-ab62-4656-bd3c-993c4c43d918	Toyota	Yaris Sedan
4f387b90-82ca-4870-9169-e71092fb4769	Toyota	Yaris TS 3 Doors
f7a916d2-42ae-4c17-aa86-b121f20cba02	Toyota	Yaris TS 5 Doors
194de1c3-cd73-4013-8b83-692a04e4059a	Toyota	Yaris Verso
a921e48e-8859-499b-8b41-5d9b8f2ec752	Toyota	bZ (US)
317a3032-9cdc-4660-a990-6a07cc9174b9	Toyota	bZ4X
7889b95b-f51c-40eb-b733-a2a58999944c	Toyota	bZ4X Touring
9b99f13c-8c85-4a12-90c5-09a6f109eed4	Toyota	iQ
609fe3b0-c8f7-4486-a550-8c702d20ebf6	Vauxhall	Agila
6e5f6a1c-9bf1-42d6-9feb-114169d70271	Vauxhall	Antara
c381959e-66cb-47da-8265-75c5ad17e6e1	Vauxhall	Astra Estate
ec8473a0-3b4f-402f-9de1-c3efd7e27db2	Vauxhall	Astra Hatchback
350fd0d6-b49c-4167-a524-bda4e6cd5355	Vauxhall	Astra Sport Hatch
1a7ac805-d39a-4149-85ca-c8721e8c610d	Vauxhall	Astra Twin Top
30ac7726-4509-42a9-b644-1d513c2f427c	Vauxhall	Astra VXR
b4561ccd-2482-4245-8643-11ef41f7fbe4	Vauxhall	Corsa 3 Doors
44053563-0dcf-4bce-89e0-3afb43b1d129	Vauxhall	Corsa 5 Doors
31c43e27-4482-4c96-ae48-1bb51fea0288	Vauxhall	Corsa VXR
532859c8-5144-4ac3-a21f-c203db388ff4	Vauxhall	Crossland X
38882b23-4192-4751-8738-1351b6c9b02c	Vauxhall	Grandland
1fe5196d-48ec-43db-a0ea-c8ae68031917	Vauxhall	Insignia Country Tourer
7d1c8b5f-fb54-427d-8609-1924a9ac59ef	Vauxhall	Insignia Hatchback
6d83310e-dc0d-416a-b6cc-df0e033ebabe	Vauxhall	Insignia Sedan
6e4f0b13-6ea6-42c6-8c62-64ab2b598004	Vauxhall	Insignia Sports Tourer
0f4431b5-9143-471d-87d5-3fd0a501cbbd	Vauxhall	Insignia VXR Sedan
008fdd57-662c-4853-981a-7dfc312e2eb7	Vauxhall	Insignia VXR Sports Tourer
454e7144-542c-4311-a0bb-e3fd64455101	Vauxhall	Meriva
be151963-ba1c-4c32-a750-366bcdd1b803	Vauxhall	Meriva VXR
11462106-493b-43c9-a8cd-1e0879846702	Vauxhall	Mokka
be45b627-23da-4295-9d4d-645cfaae1d3f	Vauxhall	Mokka X
1443b1fa-dc50-4924-bfa0-dd18dfda6e63	Vauxhall	Monaro
ca2b67df-96d5-45d4-b96e-e2202bbf4fde	Vauxhall	Monaro VXR
7b663c9b-a1b2-4995-9f18-750dc2a6a29d	Vauxhall	Signum
0b8bd4e7-6063-4de4-a51d-2e99adc147aa	Vauxhall	Tigra Twin Top
87b5018d-dd2c-41a5-a1e9-d3526d645cd9	Vauxhall	VX 220
1c1e3da8-6fc4-4702-8af4-d35852924a16	Vauxhall	VXR8
d1523337-36db-4285-9259-301bf8a19c7c	Vauxhall	Vectra Estate
1787b562-e448-42e1-b22b-da43c28362b1	Vauxhall	Vectra Estate VXR
9cec7f68-74a1-4057-a7bc-95c0fcb09804	Vauxhall	Vectra Hatchback
c87204d2-5b8b-4967-a2a7-106f756af6dc	Vauxhall	Vectra Saloon
0dbb60ff-78bf-4b94-9ae0-fb76005da7cf	Vauxhall	Vectra VXR
3f4f863f-b7cf-4c14-aabc-d706b773691f	Vauxhall	Viva
0bb9553f-2413-4075-91ef-b1dece8c094f	Vauxhall	Zafira
a3a083b8-3887-43db-b240-dc78949ea7ad	Vauxhall	Zafira VXR
ba40ca0e-01d6-4bdb-b29f-f8fcdbfdf752	Volkswagen	Amarok
42af3748-fe29-4c17-b896-250354dcdb00	Volkswagen	Ameo
cf302b94-7fa6-438a-85dc-e8cc734386aa	Volkswagen	Arteon
5cf27b19-a7fa-4dda-a346-e6537b23b6ba	Volkswagen	Arteon Shooting Brake
77420bb5-5f62-4ac1-bbab-e7e7d0889352	Volkswagen	Atlas
52efaccd-c000-4bbc-b8d0-8bc742406110	Volkswagen	Atlas Cross Sport
1a5aba05-dcfc-4748-b446-6f3cb9491f5f	Volkswagen	Beetle
c9ebc697-dc5d-4a2d-b51f-36714bd83852	Volkswagen	Beetle Cabrio
75f24c11-9486-4182-af9e-05c3eb7b74e3	Volkswagen	CC
80c4ee49-5a1f-4e64-981f-ed7b653e4a8c	Volkswagen	Caddy
db686337-c851-4347-a2cc-9a46b635d278	Volkswagen	Corrado
0d88452c-2ce3-4458-90bf-b4a10eb4662f	Volkswagen	Cross UP!
277663f8-8c2e-4fed-9e85-8030bbb7fb9f	Volkswagen	Derby
34be44da-72ba-446c-acb6-8ba7c4be1923	Volkswagen	Eos
a47d21cc-ea39-4386-a9f4-9742807e95db	Volkswagen	Fox
74b61898-b36e-4e69-8b25-015fe94857cb	Volkswagen	Gol
132b7984-07b0-4e81-a2e4-759d46aec83b	Volkswagen	Golf 3 Doors
4ea006ad-0a90-46e4-af83-3b30b8e3bd2a	Volkswagen	Golf 5 Doors
7f21675d-6c3a-4d95-9848-867653f7d744	Volkswagen	Golf Alltrack
f08f0eca-9649-4d0d-8c99-d4959471d541	Volkswagen	Golf Cabrio
37cfdd5b-8a90-4e2e-846e-449344f0f97f	Volkswagen	Golf GTD Variant
c57439b9-ffa1-40ca-b19e-ffd75d734b03	Volkswagen	Golf GTI / GTD / GTE
9f25ab71-bffa-4810-a6b2-5e7c45b87bf1	Volkswagen	Golf GTI cabrio
bd41914c-0d91-4a1b-8e6e-a8accfcb4322	Volkswagen	Golf Plus
4ccf135e-0ac1-40dc-bd2f-d8b565771c68	Volkswagen	Golf R
614aac0b-af14-46da-9957-e34890c257ba	Volkswagen	Golf R Cabrio
5c556359-ccf2-456e-b58b-4b5d65e611eb	Volkswagen	Golf R Variant
fe8a2ec2-3faf-4739-8e9a-98d294639974	Volkswagen	Golf Sportsvan
cfb537fa-478a-41ac-aa90-05773b39b7c0	Volkswagen	Golf Variant
b6b94c5e-f991-484b-904f-d4bca888c2a5	Volkswagen	ID. Buzz
4d0ae577-c797-4f5a-b694-11ed690c851e	Volkswagen	ID.3
a14befbd-80f1-4f9a-8c8f-dcfed238d9d4	Volkswagen	ID.3 GTX
458cee36-c9b0-4c61-8bd6-6ac3f6039af8	Volkswagen	ID.4
39c1e89c-c561-4a9c-90e6-d5d1a8133d84	Volkswagen	ID.5
dbe0930f-b8cb-4b7c-87e6-42980c6d1311	Volkswagen	ID.5 GTX
e0953399-1285-41bb-bda4-e631e5702fd7	Volkswagen	ID.7
a2e83222-1fd8-41e4-b7e9-9fbc31e253f7	Volkswagen	ID.7 GTX
62633a5c-0db5-44ca-a426-d2f15812b6cf	Volkswagen	ID.7 GTX Tourer
546363ac-83c8-47e6-8060-68cd2936d08d	Volkswagen	ID.7 Tourer
ce2ffd3d-7338-4a40-b202-6931f526f8fa	Volkswagen	Jetta / Vento / Bora
858a9c1b-4318-4354-ab1d-bfe41644947c	Volkswagen	Jetta GLI
abbd3ed5-3cb7-4f08-9e74-1288e3aa81ba	Volkswagen	Lavida (China)
d6acb9f8-6820-410b-99be-5459b2ca2427	Volkswagen	Lupo
10015688-8848-4c15-9d62-c49d5fbdc8c0	Volkswagen	Multivan
1ae32283-8198-40b1-aff9-a14b40b8b6b7	Volkswagen	Nivus
84b279b1-0841-4179-ada4-b040759f2a81	Volkswagen	Passat
b1fa99ae-151a-4c98-a666-d0a1966b536e	Volkswagen	Passat 3 Doors
7e32b725-d952-46a6-b1ae-22107cff3480	Volkswagen	Passat Alltrack
29cb4939-aed0-4e91-b8ef-6bcf8616c3a3	Volkswagen	Passat GTE
47cd5078-3732-4202-a223-e453a06728d4	Volkswagen	Passat Hatchback
30d00aa9-cf6d-4e7b-bbc4-de7991307eba	Volkswagen	Passat R
b6988887-e4e1-4bf7-89c3-89f1e6036ccf	Volkswagen	Passat Variant
f92635eb-cbfb-42e4-93c1-a6b3ed11008a	Volkswagen	Passat Variant GTE
77945ead-52c8-42a1-ae84-4c88188bbb3d	Volkswagen	Phaeton
349e30ea-9eb2-48e8-9799-770342b4fb63	Volkswagen	Pointer
2ee374cb-45e6-4d9d-b9b2-b90ce9077bdc	Volkswagen	Polo 3 Doors
b1902403-c265-4f2c-af2d-2d66fab09f86	Volkswagen	Polo 5 Doors
c6b154eb-cee4-435e-8193-003c380afcad	Volkswagen	Polo BlueGT
936c8338-b3f3-44cc-a9fb-c4b4e91130c2	Volkswagen	Polo Coupe
499b0fbf-e8be-4271-a696-a02ad16fbab0	Volkswagen	Polo GTI
ad610d6d-b611-4403-8d7a-0c57eacebd07	Volkswagen	Polo Sedan
ae1ffb28-31bd-4f9f-8322-8e9d3ac0e231	Volkswagen	Polo Variant
798f1f54-d88e-402c-88ac-760602c86413	Volkswagen	Routan
c2091e14-84e2-465f-8acc-a4e36c9427e2	Volkswagen	Santana
c504ef65-000d-49f5-ace9-6a4375ff3824	Volkswagen	Scirocco
d29c3e67-2145-499e-ad40-fa64b38a1cea	Volkswagen	Scirocco R
f2761e78-3bdd-49a3-82ce-0d9289ee6e1e	Volkswagen	Sharan
6a3bfec1-3931-485e-9464-96131c919514	Volkswagen	T-Cross
f0d24cfc-84f6-4141-a8c7-44af7f356104	Volkswagen	T-Roc
92040ff8-3323-47dc-be0c-a37b940b1d0a	Volkswagen	T-Roc Cabriolet
8375ad85-3342-4b6d-95e6-a93895454922	Volkswagen	T-Roc R
9fa95bf5-2267-43e5-b03d-d4c85c72ea20	Volkswagen	Taigo
37cd6bea-9252-4fca-8de7-e311912fd9ff	Volkswagen	Taos
80a06148-ff9f-4701-984a-f61a2905290b	Volkswagen	Tayron
5984fa33-90ca-4dec-a6a2-3d7d1c107777	Volkswagen	Tiguan
698d0c61-576b-443a-9da9-90b6ff18882b	Volkswagen	Tiguan (US)
05238f7f-e2d5-4025-95d8-9e75fc0f64b2	Volkswagen	Tiguan Allspace
d596b0a2-21fb-4c00-a641-ee838a445295	Volkswagen	Touareg
6867fa62-4580-436a-9389-2d47efe5b017	Volkswagen	Touareg R
136e5c0c-7c2d-4f6b-a500-5c7bbf20f07d	Volkswagen	Touran
18eaccc8-cd5c-4c20-9479-34a2789314b9	Volkswagen	Transporter Kombi
7025586e-e62e-4005-991a-b7894dab0aa8	Volkswagen	Vento
16855398-9f99-4b3f-9b43-8d7ea89d1949	Volkswagen	XL1
81cc77fb-69b2-4ca6-8c9d-dc0af5d0570b	Volkswagen	e-Golf
473ea44f-0fd6-4023-8a6f-c6cc131f4436	Volkswagen	e-UP!
7ddc9bc3-6df9-47c7-b6b3-6e5bce5dfb7e	Volkswagen	up!
209b2f24-abf9-4920-8662-c2d2ceda777e	Volkswagen	up! GTI
859642fc-0a13-41c4-b20c-e71f8fd2c88d	Volvo	142
970c7e0e-70aa-4954-a2b6-1a5e690e3b53	Volvo	144
86fae033-be70-40d1-9501-ee9e5d93fb3e	Volvo	145
09acecfb-0e23-4016-83ad-cfc482c70e7b	Volvo	244
8fd1acef-997d-44a5-bd18-51bbe4d1a809	Volvo	245
7d3c5c5f-5bcc-49e8-8e9c-f847db24b27b	Volvo	262
21aa05be-06b4-42b2-bfd6-92e561344b07	Volvo	264
7a935301-0984-4337-adcb-824f489f39da	Volvo	265
e04d9dac-90af-4bff-9bef-6a61fccb6307	Volvo	343
7276dc68-fd19-4f06-b6ba-cbcb1f086e7a	Volvo	345
73ad9c75-2538-412e-a727-1ec3fa543309	Volvo	440
09c8da3a-af15-46ac-93de-911b721a4e71	Volvo	460
441c6ef3-81ea-4b1c-830b-43dcc1d3c80d	Volvo	480
0b654167-f7c1-4ad4-a396-35b92654b733	Volvo	66
a71183c2-434c-4899-af64-6f91e50710dc	Volvo	760
79506d96-c74d-4359-a33a-10bed47f022c	Volvo	760 Estate
eecedfee-8ce5-47a4-89ce-6258cdc0d97b	Volvo	780
40fef2a6-46eb-4cd3-8ab0-54776658af37	Volvo	850
1208e40e-b21a-481e-9514-f2b6fd7aa957	Volvo	850 Estate
1f566d09-9132-4fa2-b235-75bc4e1b99a3	Volvo	940
9db96090-dbd7-4012-a16e-a5cb6cae661b	Volvo	940 Estate
d191e1b2-03f7-4547-aa9b-77e0c958b815	Volvo	960
607d5b4d-4854-4ab0-afa5-b5c0a46edcb3	Volvo	960 Estate
84228084-bff7-4b75-afdf-a70afc1cbf37	Volvo	C30
f7a0a85b-2b03-455d-aa59-ec4257616c4f	Volvo	C40
6846673e-f790-4c0e-bc92-0f395eef0b0f	Volvo	C70
46071e77-c5e5-455d-ad95-e159514507c7	Volvo	C70 Convertible
ae4246d7-64d4-496b-9b0e-aed1e8b0196a	Volvo	C70 Coupe
39b10da7-dabb-455c-a217-68424377a475	Volvo	EM90
f7215e43-7f33-4eec-b837-82f2ae068936	Volvo	ES90
ead03f85-a95a-4ae0-b078-db46e6916d29	Volvo	EX30
de2d05a0-b621-4b9c-86fe-f4383e1e670a	Volvo	EX30 Cross Country
001fa08f-152e-493a-8af6-76f26dccf232	Volvo	EX60
4bf08b1b-b97e-4cf1-8bc0-891b2c9652f3	Volvo	EX60 Cross Country
71694a95-301e-473f-b668-77952c321df7	Volvo	EX90
df3c125d-1ea4-4f9d-89dc-7c1f003939a3	Volvo	P1800
e697da30-f48c-4514-aa2c-41a4d7830f34	Volvo	S40
0d953eab-bb15-437d-89f0-732ef2959b90	Volvo	S60
85f7528f-1bc2-406e-a1b0-836641d5cd4a	Volvo	S60 Cross Country
6524d05a-df38-4dc4-a49e-b901cf90fe6f	Volvo	S60 Polestar
c2c6fb21-e2a4-4540-b907-73c7febe0ea7	Volvo	S70
9c39209d-0232-4e05-9a41-7c5bc5489f38	Volvo	S80
8b45c67d-55ee-47f7-acfb-d70de489afd0	Volvo	S90
e4811221-351c-429d-99da-9e3fc1aed796	Volvo	V40
ac30b36b-cad2-4ff4-a2aa-ec8cec7e4a50	Volvo	V40 Cross Country
83bfed8e-eb3a-45d2-bbf0-387c7759e3c7	Volvo	V50
69dc89fe-5ddb-4229-b513-3d3d48a5d726	Volvo	V60
852d71c0-9c8f-4a04-881d-2ea42d0eadba	Volvo	V60 Cross Country
ed96e9ef-9c29-4cd4-bcc1-cc42e7a4ef8a	Volvo	V60 Polestar
02e78dd2-eb16-4124-892c-9a2aca36d911	Volvo	V70
fd9c6d9e-cd66-4df0-a887-8066348512b1	Volvo	V90
eeaaa892-b54d-4720-b62d-e43069e753bd	Volvo	V90 Cross Country
7ee3019f-251f-470b-80ed-2a2b5837001a	Volvo	XC40
2a0e702d-6a7c-489e-9bc5-ac65d933430b	Volvo	XC60
1be1da83-6e02-4d38-b3bd-3d40abf54a80	Volvo	XC60 Polestar
15403ee4-de4b-42ea-bd44-3eedeaa0441f	Volvo	XC70
9984a4e3-2d8c-41b7-8d28-b4bc05e39117	Volvo	XC90
37a41985-2d1e-44c8-82a2-eb7394faaf35	Wiesmann	GT MF4
b273cdfe-39a9-4759-93d7-170f20efe0d3	Wiesmann	GT MF4-CS
ed4d7ba3-b886-400f-acc9-6503e7d9fe80	Wiesmann	GT MF4-S
3e9a16a3-6761-4f06-99df-c8ded8aba72d	Wiesmann	GT MF5
61e873ca-0448-4bff-827b-9a51c29b5d48	Wiesmann	MF4 Roadster
5fc46731-4220-4b46-886e-852ad3574a84	Wiesmann	MF4-S Roadster
dcd08bb3-9069-40e8-bdff-b0e840ecd7e8	Wiesmann	MF5 Roadster
e3e45f54-f599-4de1-beb2-635e207852cb	Zender	Escape 6
831fdca3-0b63-42b4-8ed3-fa137a7e06ea	Zender	Fact 4 BiTurbo
013e0da4-6bea-4d63-a4b3-5323b53d35df	Zender	Fact 4 Spider
65eaebce-3e15-4f36-bd8a-3c77d406a557	Zender	Progetto 5
e54d51a2-c81b-44b3-8169-186ae50ca229	Zender	Straight 8
23735a5c-deb0-4486-bd9a-8a9a24703368	Zender	Thirty 7
a3f6c6e1-68e9-41c7-89e4-8f8e7fccf256	Škoda	100 series
a663e0f6-3731-40e8-a2c0-8265e338e6a5	Škoda	860
324cbda3-8aa6-4a9b-9afa-e27f20167616	Škoda	Citigo
02dcfcf8-1ae9-47f9-a125-4b16aad47fa5	Škoda	Elroq
c15dd279-d5da-43b4-a9cd-e0e168431a0a	Škoda	Elroq RS
43a2de1c-2df6-4e70-95eb-fd8774358d69	Škoda	Enyaq
e50cf67d-4b37-4122-8e5c-4ef49be28e6c	Škoda	Enyaq Coupe
877e9731-adbc-4fce-9958-2dda75d77693	Škoda	Enyaq Coupe RS
7f81ae91-eca9-4872-b7be-365fca36b8e6	Škoda	Enyaq RS
ab2f1749-9010-43f1-b74d-402d3ec98fae	Škoda	Fabia
2c7caaaf-6c8d-44e0-9359-b60f2c994c3a	Škoda	Fabia Combi
add47b02-e871-4e26-9dd1-235dfd772041	Škoda	Fabia Combi RS
0b97643b-efd9-4f40-9410-7fde70a23d4b	Škoda	Fabia RS
4bc3b203-94ad-4a35-ac69-668b5c88ba9a	Škoda	Fabia Sedan
b8d9c133-ed42-47da-a10b-16a0a7102f06	Škoda	Favorit
6d5d12c1-774c-4a16-8448-663eab568b07	Škoda	Favorit Estate
832b491f-88a7-492a-9095-3cee61612bf4	Škoda	Felicia
5af88789-f2d3-4ddb-a74b-59fddc74e967	Škoda	Felicia Combi
a689db25-ba5a-4c54-95ec-a222f182eac7	Škoda	Kamiq
1290e01c-fc8d-4898-8c9a-e585b2a4cdf0	Škoda	Karoq
a0a56650-90f3-4ecf-88b7-20b285c9d5a1	Škoda	Kodiaq
dff8a7f4-7817-4b76-898f-948329136b8c	Škoda	Kodiaq RS
d4e0d810-7a7b-4fe2-9202-f6c9d0a6ff7b	Škoda	Kushaq
79f8c62d-afde-48c2-9cae-73c4e0bcf10c	Škoda	Kylaq
4f437b17-4f1f-4e8a-b33b-35c0e6b56172	Škoda	Octavia
1a9de9de-7b20-4fa6-847f-c0a05abb71b6	Škoda	Octavia Combi
e09cb507-efe7-4ba2-a3ee-d75595300f95	Škoda	Octavia Combi 4x4
ebe0c5f6-840b-40ea-acb6-689a96dc570e	Škoda	Octavia Combi RS
00ec53c4-dd27-426c-be5b-08898a2b1422	Škoda	Octavia RS
1ff94e01-0f08-4266-be9a-7eecfd4c7f0c	Škoda	Octavia Scout
cc0774b1-abc9-4370-a851-a5d21212f757	Škoda	Rapid
6150d980-be53-418c-9d2a-732fb6b3a3cd	Škoda	Rapid Spaceback
225da31d-77fb-4167-95f4-bc33a492a865	Škoda	Roomster
d3af21af-7a6d-49ed-90ee-d9f2d60ab2aa	Škoda	Scala
2cd18d5c-1f2d-41d5-8988-a0c909545da2	Škoda	Slavia
d37e2911-204f-440c-8bae-cdf29da616f9	Škoda	Superb
407f51bc-ad56-4dde-8d92-fa91d0e7f3f2	Škoda	Superb Combi / Scout
61ddaaa8-9c67-493f-9ff0-68a5d4d1fdf8	Škoda	Yeti
\.


--
-- Data for Name: comments; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.comments (id, user_id, post_id, comment_text, created_at, updated_at) FROM stdin;
eff3b59b-5b62-4d1f-8934-6847ac1ebe27	aea27264-e0e1-4981-9ffd-104046764c62	d6fdb192-f080-4a04-ac7a-74a8eaf696c0	Foarte tare!	2026-06-21 18:25:35.222531	2026-06-21 18:25:35.222531
99d8ef30-29db-45ac-9099-effafd47f820	c8b2cbc4-3855-4a2a-b960-a9db534c7c8e	d0d6431b-1068-44e6-bd5c-82aeecb83d85	hchcj	2026-06-26 17:44:46.540309	2026-06-26 17:44:46.540309
8bb74af0-b166-4c65-82a4-ee4dcf68290e	ab55e2ee-9e0b-493b-9f90-a7bdbfe32f70	000f1a08-0aa6-4fe3-afb7-de2dd9e5ca37	jbhj	2026-06-26 17:45:07.992408	2026-06-26 17:45:07.992408
dc54cb10-3d73-4763-b721-6134a7ebb768	ab55e2ee-9e0b-493b-9f90-a7bdbfe32f70	000f1a08-0aa6-4fe3-afb7-de2dd9e5ca37	hcfug	2026-06-26 17:45:19.396528	2026-06-26 17:45:19.396528
483a6348-b1c5-46fb-a5dd-0772b1a5cc55	c8b2cbc4-3855-4a2a-b960-a9db534c7c8e	d0d6431b-1068-44e6-bd5c-82aeecb83d85	hcfucug	2026-06-26 17:45:23.088448	2026-06-26 17:45:23.088448
df507a7b-4300-4518-986f-e4f56cb8290d	c8b2cbc4-3855-4a2a-b960-a9db534c7c8e	000f1a08-0aa6-4fe3-afb7-de2dd9e5ca37	uhhh	2026-06-26 17:45:45.917739	2026-06-26 17:45:45.917739
0a3e8d9a-abb8-4daa-94fe-6cef3963d292	ab55e2ee-9e0b-493b-9f90-a7bdbfe32f70	5461df18-d817-40cc-9af1-fced1e5f6d1e	asa da	2026-06-26 18:25:59.119927	2026-06-26 18:25:59.119927
\.


--
-- Data for Name: early_spotter_counter; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.early_spotter_counter (id, last_assigned) FROM stdin;
1	2
\.


--
-- Data for Name: flyway_schema_history; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success) FROM stdin;
1	1	init	SQL	V1__init.sql	232960084	carspotter_dev	2026-06-19 19:17:30.078694	39	t
2	2	feed keyset index	SQL	V2__feed_keyset_index.sql	1096719107	carspotter_dev	2026-06-19 19:17:30.134201	2	t
3	3	post town country	SQL	V3__post_town_country.sql	-1999695304	carspotter_dev	2026-06-20 18:23:38.07119	8	t
4	4	reports	SQL	V4__reports.sql	1545241934	carspotter_dev	2026-06-21 17:32:20.873278	30	t
5	5	seed car models	SQL	V5__seed_car_models.sql	-1606880851	carspotter_dev	2026-06-21 22:26:54.086518	33	t
6	6	normalize user profile picture paths	SQL	V6__normalize_user_profile_picture_paths.sql	-852540921	carspotter_dev	2026-06-21 23:41:26.204149	10	t
7	7	posts user keyset index	SQL	V7__posts_user_keyset_index.sql	-349634977	carspotter_dev	2026-06-22 18:00:28.452247	11	t
8	8	post source and timezone	SQL	V8__post_source_and_timezone.sql	-1960930280	carspotter_dev	2026-06-22 22:57:33.720924	15	t
9	9	user streak columns	SQL	V9__user_streak_columns.sql	860801100	carspotter_dev	2026-06-22 22:57:33.749647	2	t
10	10	post points	SQL	V10__post_points.sql	-1599968911	carspotter_dev	2026-06-23 16:36:00.864384	12	t
11	11	user streak timezone	SQL	V11__user_streak_timezone.sql	480865143	carspotter_dev	2026-06-23 23:16:10.6205	4	t
12	12	leaderboard rank snapshots	SQL	V12__leaderboard_rank_snapshots.sql	-1775259352	carspotter_dev	2026-06-23 23:16:10.636072	10	t
13	13	auth sessions	SQL	V13__auth_sessions.sql	139696131	carspotter_dev	2026-06-24 01:31:47.966416	15	t
14	14	early spotter	SQL	V14__early_spotter.sql	631265869	carspotter_dev	2026-06-27 14:41:25.08852	20	t
\.


--
-- Data for Name: friend_requests; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.friend_requests (sender_id, receiver_id, created_at) FROM stdin;
\.


--
-- Data for Name: friends; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.friends (user_id_1, user_id_2, created_at) FROM stdin;
\.


--
-- Data for Name: leaderboard_rank_snapshots; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.leaderboard_rank_snapshots (snapshot_date, user_id, rank, spot_score) FROM stdin;
\.


--
-- Data for Name: likes; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.likes (id, user_id, post_id, created_at) FROM stdin;
deec99b5-9f37-44c2-a208-698b96140033	aea27264-e0e1-4981-9ffd-104046764c62	b97be0eb-cb72-4aea-8c92-5baf07afe4de	2026-06-21 20:55:35.832098
3bc94482-1b0b-4ea4-83d1-ea99ff59f4a2	c8b2cbc4-3855-4a2a-b960-a9db534c7c8e	4afd3b84-1b70-43b6-a7db-b6a3f15ad44f	2026-06-22 18:37:21.78941
05f7cd7f-6a63-4233-b19d-3f443a45e877	aea27264-e0e1-4981-9ffd-104046764c62	d6fdb192-f080-4a04-ac7a-74a8eaf696c0	2026-06-24 02:35:16.585262
bad38cbe-ab48-49ed-a855-19a315575cad	aea27264-e0e1-4981-9ffd-104046764c62	dd5ba614-55c2-4362-8549-3dcc52c53c45	2026-06-24 17:17:43.186147
ac65cd50-870c-4f46-896c-c73c74e4acc8	c8b2cbc4-3855-4a2a-b960-a9db534c7c8e	000f1a08-0aa6-4fe3-afb7-de2dd9e5ca37	2026-06-24 17:19:22.247683
becd9ddd-dc29-4160-97c8-34340279b27f	ab55e2ee-9e0b-493b-9f90-a7bdbfe32f70	5461df18-d817-40cc-9af1-fced1e5f6d1e	2026-06-26 18:25:55.511403
\.


--
-- Data for Name: posts; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.posts (id, user_id, car_model_id, custom_brand, custom_model, image_path, description, latitude, longitude, created_at, updated_at, town, country, source, created_at_timezone, points) FROM stdin;
000f1a08-0aa6-4fe3-afb7-de2dd9e5ca37	aea27264-e0e1-4981-9ffd-104046764c62	\N	Mercedes-Benz	CL 63 AMG	posts/2026/06/19/995d342d-9fc6-4e88-bdc5-9e387237f16a.jpg	Clean CL63 AMG spotted in traffic today.	50.8503	4.3517	2026-06-19 13:00:00	2026-06-20 18:33:49.746848	Brussels	Belgium	GALLERY	\N	0
d0d6431b-1068-44e6-bd5c-82aeecb83d85	aea27264-e0e1-4981-9ffd-104046764c62	\N	Lamborghini	Urus	posts/2026/06/18/59c9a48f-3029-40e6-b998-2db2d3a8f455.jpg	Widebody Lamborghini Urus finished in a unique bronze spec.	45.7489	21.2087	2026-06-19 02:00:00	2026-06-20 18:33:49.746848	Timisoara	Romania	GALLERY	\N	0
22752f38-7f29-470b-9d07-a56b716ef7bd	aea27264-e0e1-4981-9ffd-104046764c62	\N	Lamborghini	Urus	posts/2026/06/18/29ab44cb-c0ce-4a9c-9364-49f341be108d.jpg	Another clean Lamborghini Urus spotted in Bucharest.	44.4521	26.0835	2026-06-18 15:00:00	2026-06-20 18:33:49.746848	Bucharest	Romania	GALLERY	\N	0
1c72db7a-6daa-4027-8de3-198aba18cc69	aea27264-e0e1-4981-9ffd-104046764c62	\N	Lamborghini	Revuelto	posts/2026/06/18/83a807bc-9a70-4f23-9b9b-b6376dfbb49c.jpg	Lamborghini Revuelto spotted in Oradea.	47.0722	21.9211	2026-06-18 04:00:00	2026-06-20 18:33:49.746848	Oradea	Romania	GALLERY	\N	0
0883f99b-0a29-4df1-936d-2f375488ef08	aea27264-e0e1-4981-9ffd-104046764c62	\N	Porsche	911 Turbo S	posts/2026/06/17/0a637026-68ce-4ab5-ad8f-2b2e6c71cbdc.jpg	Beautiful 911 Turbo S sitting low in Timisoara.	45.7489	21.2087	2026-06-17 17:00:00	2026-06-20 18:33:49.746848	Timisoara	Romania	GALLERY	\N	0
d6fdb192-f080-4a04-ac7a-74a8eaf696c0	c8b2cbc4-3855-4a2a-b960-a9db534c7c8e	\N	McLaren	GT	posts/2026/06/17/2720409e-b98b-4f0e-b0b4-786fc1fa6a4f.jpg	McLaren GT looking unreal on Slovak roads.	48.1486	17.1077	2026-06-17 21:00:00	2026-06-20 18:33:49.746848	Bratislava	Slovakia	GALLERY	\N	0
dd5ba614-55c2-4362-8549-3dcc52c53c45	c8b2cbc4-3855-4a2a-b960-a9db534c7c8e	\N	BMW	M6	posts/2026/06/17/6a4d0e93-b04a-4a6a-b86e-ba1eaf273249.jpg	Old-school V8 power spotted in Bucharest.	44.4396	26.0963	2026-06-17 10:00:00	2026-06-20 18:33:49.746848	Bucharest	Romania	GALLERY	\N	0
3830b12f-31a6-4c90-9464-dd729363d2dd	c8b2cbc4-3855-4a2a-b960-a9db534c7c8e	\N	Porsche	911 Turbo	posts/2026/06/16/a9fb3572-1115-4846-b8ed-d61181fca05f.jpg	Classic Turbo shape hiding in plain sight.	44.4521	26.0835	2026-06-16 23:00:00	2026-06-20 18:33:49.746848	Bucharest	Romania	GALLERY	\N	0
f463236e-0046-4d1f-8b01-1cecee6ccd4b	c8b2cbc4-3855-4a2a-b960-a9db534c7c8e	\N	Bentley	Flying Spur	posts/2026/06/16/072e506f-f8db-4b97-b776-b77e2898380e.jpg	Proper luxury sedan spotted in Buzau.	45.15	26.8167	2026-06-16 12:00:00	2026-06-20 18:33:49.746848	Buzau	Romania	GALLERY	\N	0
4afd3b84-1b70-43b6-a7db-b6a3f15ad44f	c8b2cbc4-3855-4a2a-b960-a9db534c7c8e	\N	Rolls-Royce	Ghost	posts/2026/06/15/18a768bb-b19d-47a2-9f6e-3caac568ee9f.jpg	Ghost presence on a quiet street in Iasi.	47.1585	27.6014	2026-06-16 01:00:00	2026-06-20 18:33:49.746848	Iasi	Romania	GALLERY	\N	0
209248c4-a3a5-4dc4-a9ab-026b39b6be05	ab55e2ee-9e0b-493b-9f90-a7bdbfe32f70	\N	BMW	M3 Competition Touring	posts/2026/06/16/d6b6de87-128c-4397-93bb-17656b3b14cb.jpg	The dream daily wagon, spotted in Bucharest.	44.466	26.085	2026-06-16 05:00:00	2026-06-20 18:33:49.746848	Bucharest	Romania	GALLERY	\N	0
868ca08d-53e0-464b-838d-95f835002ec3	ab55e2ee-9e0b-493b-9f90-a7bdbfe32f70	\N	Audi	RS7	posts/2026/06/15/22ee7b5e-74b3-4b45-94a1-8a5246d50d40.jpg	Wide RS7 stance somewhere in Munich.	48.1351	11.582	2026-06-15 18:00:00	2026-06-20 18:33:49.746848	Munich	Germany	GALLERY	\N	0
2481ce48-85d7-4c6f-8259-1b25351b2a5b	ab55e2ee-9e0b-493b-9f90-a7bdbfe32f70	\N	Rolls-Royce	Cullinan	posts/2026/06/15/ddecbbd5-2edd-48ad-9467-06cc5ac4bb97.jpg	Massive Cullinan presence in Cluj-Napoca.	46.7712	23.6236	2026-06-15 07:00:00	2026-06-20 18:33:49.746848	Cluj-Napoca	Romania	GALLERY	\N	0
638ccb8d-42f7-4447-9d40-d666b2cd2df1	ab55e2ee-9e0b-493b-9f90-a7bdbfe32f70	\N	Lamborghini	Urus	posts/2026/06/14/63d7eb16-5b09-4af9-bb0d-ef5312de98f3.jpg	Another Urus spotted roaming through Iasi.	47.1585	27.6014	2026-06-14 20:00:00	2026-06-20 18:33:49.746848	Iasi	Romania	GALLERY	\N	0
e5afe7a7-0be5-47b4-aaaf-efacc8392b9e	bf8cb9db-fa64-4294-a4c6-1e8aa4cbf274	\N	Mercedes-AMG	GT	posts/2026/06/14/21a97217-d6b2-4900-b3d7-c2cdb624c18e.jpg	AMG GT parked outside a restaurant in Bucharest.	44.4555	26.0975	2026-06-14 09:00:00	2026-06-20 18:33:49.746848	Bucharest	Romania	GALLERY	\N	0
4996f540-0092-42d0-944b-e5e00598a54c	bf8cb9db-fa64-4294-a4c6-1e8aa4cbf274	\N	Porsche	911	posts/2026/06/14/5d823b1c-089c-432e-94ef-9fcffc6240fc.jpg	Clean 911 spotted in downtown Timisoara.	45.7489	21.2087	2026-06-14 13:00:00	2026-06-20 18:33:49.746848	Timisoara	Romania	GALLERY	\N	0
5ddf11ac-b5bd-4ee8-a7e3-40c1ff6dbf86	bf8cb9db-fa64-4294-a4c6-1e8aa4cbf274	\N	Koenigsegg	Jesko	posts/2026/06/13/f06067fc-abad-4c67-9cf0-9f416b99bd0a.jpg	Couldn't believe my eyes. Jesko in Bucharest.	44.481	26.0897	2026-06-14 02:00:00	2026-06-20 18:33:49.746848	Bucharest	Romania	GALLERY	\N	0
ee23587d-bdb1-406d-80eb-079f736f2e6a	bf8cb9db-fa64-4294-a4c6-1e8aa4cbf274	\N	Porsche	918 Spyder	posts/2026/06/13/936bfa33-c209-45a7-ae6e-3ff1a4647d74.jpg	918 Spyder spotted on the streets of Rome.	41.9028	12.4964	2026-06-13 15:00:00	2026-06-20 18:33:49.746848	Rome	Italy	GALLERY	\N	0
b97be0eb-cb72-4aea-8c92-5baf07afe4de	b128e98a-984e-4c8f-a537-8c7d9b576a0c	\N	Ferrari	SF90 Spider	posts/2026/06/13/0dac45b9-2737-4fca-b739-d2ad2d6d49be.jpg	SF90 Spider cruising through the French countryside.	43.7102	7.262	2026-06-13 04:00:00	2026-06-20 18:33:49.746848	Nice	France	GALLERY	\N	0
62cddc4f-986a-4a42-90a8-78bfee0f42a3	b128e98a-984e-4c8f-a537-8c7d9b576a0c	\N	Ferrari	Testarossa	posts/2026/06/12/46d3e8af-f2bb-47ff-91f9-cd2b5cfeafea.jpg	Ferrari Testarossa parked in Bucharest.	44.4268	26.1025	2026-06-12 17:00:00	2026-06-20 18:33:49.746848	Bucharest	Romania	GALLERY	\N	0
b1bc41e1-3db5-4fde-9cb6-4f543306d58c	b128e98a-984e-4c8f-a537-8c7d9b576a0c	\N	Mercedes-Maybach	GLS 600	posts/2026/06/12/25f1dd77-217b-48d7-8b89-badb5ef0cd90.jpg	Maybach GLS passing through a village near Targu Mures.	46.5425	24.5575	2026-06-12 21:00:00	2026-06-20 18:33:49.746848	Targu Mures	Romania	GALLERY	\N	0
2788b244-e641-45d7-bae4-d479aef5be08	b128e98a-984e-4c8f-a537-8c7d9b576a0c	\N	BMW	M2	posts/2026/06/12/41b0f2f4-d595-4f47-9d1a-383e7d1bc88c.jpg	Compact, loud and fast. BMW M2 in Bucharest.	44.492	26.121	2026-06-12 10:00:00	2026-06-20 18:33:49.746848	Bucharest	Romania	GALLERY	\N	0
cd6d3ccf-54e9-4b98-b8cc-0fc341a05c0f	99fad2b1-fe37-4e18-b931-d28f56aaa267	\N	Bugatti	Tourbillon	posts/2026/06/11/9ca9b6cd-1a02-4770-9d07-8b724244d2dd.jpg	Bugatti Tourbillon on display in Vienna.	48.2082	16.3738	2026-06-11 23:00:00	2026-06-20 18:33:49.746848	Vienna	Austria	GALLERY	\N	0
51852b4d-9555-491f-825a-b0e6f5722c9a	99fad2b1-fe37-4e18-b931-d28f56aaa267	\N	Touring Superleggera	Veloce12	posts/2026/06/11/f51fc1b8-c47c-4f22-bde3-1e49c8c1a076.jpg	Veloce12 spotted inside an underground garage in Paris.	48.8566	2.3522	2026-06-11 12:00:00	2026-06-20 18:33:49.746848	Paris	France	GALLERY	\N	0
d86ead45-eebf-4eb4-8235-fe68e9348f18	99fad2b1-fe37-4e18-b931-d28f56aaa267	\N	Ferrari	599 GTB Fiorano	posts/2026/06/10/5e3f48d5-f622-4ef5-af3f-be39195759d0.jpg	599 GTB parked along the streets of Monaco.	43.7384	7.4246	2026-06-11 01:00:00	2026-06-20 18:33:49.746848	Monaco	Monaco	GALLERY	\N	0
951d6607-1b88-4900-8aee-3ab3bf24794d	39b9c09e-db8a-4e98-87f7-32836533cb86	\N	Lamborghini	Huracan Tecnica	posts/2026/06/10/38dc4b15-f509-4482-a33a-579de44a1247.jpg	Huracan Tecnica spotted in Milan.	45.4642	9.19	2026-06-10 18:00:00	2026-06-20 18:33:49.746848	Milan	Italy	GALLERY	\N	0
920fe395-2e3d-4d7f-b020-4f1e38662cea	99fad2b1-fe37-4e18-b931-d28f56aaa267	\N	Ferrari	Daytona SP3	posts/2026/06/11/65a915bd-66d1-4616-80fd-ea74f8adcc08.jpg	Daytona SP3 attracting a crowd in Milan.	45.4642	9.19	2026-06-11 05:00:00	2026-06-20 18:33:49.746848	Milan	Italy	GALLERY	\N	0
3fea3816-ef11-4dfc-8f53-0b7c4f0c8335	39b9c09e-db8a-4e98-87f7-32836533cb86	\N	Ferrari	F40	posts/2026/06/10/174e3eb5-9f26-4588-bc04-bba810d518b7.jpg	Ferrari F40 parked in Monaco.	43.7384	7.4246	2026-06-10 07:00:00	2026-06-20 18:33:49.746848	Monaco	Monaco	GALLERY	\N	0
45500553-ff89-4999-9c95-66aeeaedf6dc	39b9c09e-db8a-4e98-87f7-32836533cb86	\N	Lamborghini	Revuelto	posts/2026/06/09/d8f56f3f-86d3-40e6-9779-70dbe2c114da.jpg	Revuelto shining under the Monaco sun.	43.7384	7.4246	2026-06-09 20:00:00	2026-06-20 18:33:49.746848	Monaco	Monaco	GALLERY	\N	0
84d318fc-a825-49b6-8db9-32ca8ddbe77f	39b9c09e-db8a-4e98-87f7-32836533cb86	\N	Pagani	Huayra	posts/2026/06/09/2807ee4d-c034-48f3-87fc-573b8fc9f9d3.jpg	Pagani Huayra casually parked in Monaco.	43.7384	7.4246	2026-06-09 09:00:00	2026-06-20 18:33:49.746848	Monaco	Monaco	GALLERY	\N	0
3b6dc1b6-30e5-4c1e-b49c-ec8344c6f2b0	80d8e2f5-5f68-458d-a6d3-e6c27f595a06	\N	Rimac	Nevera	posts/2026/06/09/211a8985-6b57-4653-8342-c789e2437f47.jpg	Nevera spotted charging in Monaco.	43.7384	7.4246	2026-06-09 13:00:00	2026-06-20 18:33:49.746848	Monaco	Monaco	GALLERY	\N	0
145bb6aa-3f7b-47a6-83a1-ebdf6084ffeb	80d8e2f5-5f68-458d-a6d3-e6c27f595a06	\N	Ferrari	Enzo	posts/2026/06/08/603be447-56ca-45ee-b3ff-ac9af0423feb.jpg	Ferrari Enzo spotted on the streets of Monaco.	43.7384	7.4246	2026-06-09 02:00:00	2026-06-20 18:33:49.746848	Monaco	Monaco	GALLERY	\N	0
efffc558-101e-4b26-8ab5-cac8a8a0894e	80d8e2f5-5f68-458d-a6d3-e6c27f595a06	\N	BMW	M4 Competition	posts/2026/06/08/d61e96fb-8a19-44c0-b554-7867c82a03b5.jpg	Frozen grey M4 Competition spotted in Bucharest.	44.4526	26.0865	2026-06-08 15:00:00	2026-06-20 18:33:49.746848	Bucharest	Romania	GALLERY	\N	0
79c129bb-1dfe-400a-af62-7141e57223c1	80d8e2f5-5f68-458d-a6d3-e6c27f595a06	\N	Ferrari	12Cilindri	posts/2026/06/08/30e85676-5bd1-4e46-b3ae-34204c1d0874.jpg	Ferrari's newest V12 spotted in Budapest.	47.4979	19.0402	2026-06-08 04:00:00	2026-06-20 18:33:49.746848	Budapest	Hungary	GALLERY	\N	0
f4939b55-f958-4e40-aa55-3d64a64db02f	7945b026-8727-4152-a3d8-7296f5a028fd	\N	Porsche	911 GT3 Touring	posts/2026/06/07/df7ac6e0-ad73-4d96-8147-9b678ba6feec.jpg	GT3 Touring spotted on a sunny day in Bucharest.	44.466	26.085	2026-06-07 17:00:00	2026-06-20 18:33:49.746848	Bucharest	Romania	GALLERY	\N	0
6adb7697-4b92-4ff8-a4b8-cd01e3f0772b	7945b026-8727-4152-a3d8-7296f5a028fd	\N	Aston Martin	Vantage	posts/2026/06/07/8e9a43f7-8ced-439e-b489-8865126a235b.jpg	Orange Vantage standing out from the crowd.	44.4268	26.1025	2026-06-07 21:00:00	2026-06-20 18:33:49.746848	Bucharest	Romania	GALLERY	\N	0
c9840a87-f663-44fb-8654-be4f01493fda	7945b026-8727-4152-a3d8-7296f5a028fd	\N	Audi	R8	posts/2026/06/07/1e9fea88-46a5-4470-a12d-9815546a6391.jpg	Purple R8 with an aggressive setup in Bucharest.	44.4555	26.0975	2026-06-07 10:00:00	2026-06-20 18:33:49.746848	Bucharest	Romania	GALLERY	\N	0
96f23190-ae90-4ca2-94a0-a0903c143df9	7945b026-8727-4152-a3d8-7296f5a028fd	\N	BMW	850i (E31)	posts/2026/06/06/067d1986-d55b-48d9-af26-7c2f27d13de8.jpg	Rare BMW E31 spotted near the city center.	44.481	26.0897	2026-06-06 23:00:00	2026-06-20 18:33:49.746848	Bucharest	Romania	GALLERY	\N	0
a82408ec-fe2c-4f03-a7e1-46854f21c153	b838b931-b37a-4761-b762-48a6a27407fd	\N	BMW	M4 Competition	posts/2026/06/06/f5830e8a-8c3f-4ce3-b598-fc5b24f48a6c.jpg	M4 Competition looking sharp in Bucharest traffic.	44.4396	26.0963	2026-06-06 12:00:00	2026-06-20 18:33:49.746848	Bucharest	Romania	GALLERY	\N	0
c5939db6-dd56-4015-9b78-6d2dab8a73e5	b838b931-b37a-4761-b762-48a6a27407fd	\N	Lamborghini	Temerario	posts/2026/06/05/6f80c231-8377-4698-8622-02ab59a250ae.jpg	First Temerario I've seen in Bucharest.	44.4521	26.0835	2026-06-06 01:00:00	2026-06-20 18:33:49.746848	Bucharest	Romania	GALLERY	\N	0
fdacf9dc-82f2-4833-b0df-c8c417acb7af	b838b931-b37a-4761-b762-48a6a27407fd	\N	Audi	RS6 Avant	posts/2026/06/06/81cca16b-f76e-4389-8a75-7a03173a79f4.jpg	RS6 Avant doing RS6 things in Bucharest.	44.492	26.121	2026-06-06 05:00:00	2026-06-20 18:33:49.746848	Bucharest	Romania	GALLERY	\N	0
6e12eba1-334f-412f-996d-8438a7a7da62	b838b931-b37a-4761-b762-48a6a27407fd	\N	Chevrolet	Corvette Z06	posts/2026/06/05/5d3e1846-6482-4c94-a526-f415bfa38fa0.jpg	Corvette Z06 spotted late in the afternoon.	44.4268	26.1025	2026-06-05 18:00:00	2026-06-20 18:33:49.746848	Bucharest	Romania	GALLERY	\N	0
5461df18-d817-40cc-9af1-fced1e5f6d1e	c8b2cbc4-3855-4a2a-b960-a9db534c7c8e	63ba35e0-a09d-4ef9-896f-6d302a444c9c	\N	\N	posts/2026/06/26/58036342-a9ea-4a94-a5f5-702474fb224c.jpg	jvjffuvc	\N	\N	2026-06-26 18:25:42.625711	2026-06-26 18:25:59.122308	\N	\N	CAMERA	Europe/Bucharest	16
0e327bb7-889b-46fd-a8c4-11b3552a2cf3	c8b2cbc4-3855-4a2a-b960-a9db534c7c8e	1bc55d1e-995d-481f-8743-e87a10622093	\N	\N	posts/2026/06/26/2e49e9b2-6de7-47f1-b7e6-c0aed2919cc2.jpg	te	\N	\N	2026-06-26 21:42:09.104999	2026-06-26 21:42:09.110284	\N	\N	CAMERA	Europe/Bucharest	10
9fbcee12-6a64-4501-823d-cab5d091b042	c8b2cbc4-3855-4a2a-b960-a9db534c7c8e	f64e9dfc-5a34-4fd3-bc00-9c4616420532	\N	\N	posts/2026/06/27/b55849b4-c51f-4f6c-a3f7-595954cb1d0a.jpg	yhbkj	\N	\N	2026-06-27 13:48:00.386898	2026-06-27 13:48:00.394069	\N	\N	CAMERA	Europe/Bucharest	10
9550e9cc-d56d-49d2-9602-807ee200f80d	94077bb6-3204-4818-b312-536d6e88119d	42bccae1-e1a7-4ed4-b4ec-042a9b3b95c7	\N	\N	posts/2026/06/27/31abf4b0-4ee4-4405-82ab-4f0765f71ab4.jpg	jcfuguvuc	\N	\N	2026-06-27 18:11:07.784056	2026-06-27 18:11:07.79264	\N	\N	CAMERA	Europe/Bucharest	10
\.


--
-- Data for Name: reports; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.reports (id, reporter_id, post_id, reason, status, created_at) FROM stdin;
5ec36988-e95f-49a6-98c7-5f0662ad1c93	aea27264-e0e1-4981-9ffd-104046764c62	d6fdb192-f080-4a04-ac7a-74a8eaf696c0	INCORRECT_CAR_MODEL	PENDING	2026-06-21 17:33:00.070797
11e63070-6b0c-4ef1-b4e5-9b40a9dcfc31	aea27264-e0e1-4981-9ffd-104046764c62	d6fdb192-f080-4a04-ac7a-74a8eaf696c0	DUPLICATE_POST	PENDING	2026-06-21 17:33:09.333755
f88ad6de-d026-4b39-b59c-567f3a101981	aea27264-e0e1-4981-9ffd-104046764c62	d6fdb192-f080-4a04-ac7a-74a8eaf696c0	INAPPROPRIATE_CONTENT	PENDING	2026-06-21 17:33:13.680596
\.


--
-- Data for Name: users; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.users (id, auth_credential_id, profile_picture_path, full_name, phone_number, birth_date, username, country, spot_score, role, created_at, updated_at, current_streak, longest_streak, last_streak_date, last_streak_timezone, is_early_spotter, early_spotter_number) FROM stdin;
c8b2cbc4-3855-4a2a-b960-a9db534c7c8e	a9ac4a4a-8df5-46dc-996e-086638a05c30	profile-pictures/2026/06/20/ddb727bf-b533-44f2-a2f0-70e2da51f80a.jpg	Ianis Luca	\N	1997-07-28	ianluke	Romania	36	USER	2026-06-20 18:33:49.746848	2026-06-20 18:33:49.746848	2	2	2026-06-27	Europe/Bucharest	f	\N
bf8cb9db-fa64-4294-a4c6-1e8aa4cbf274	eaa28bb7-534f-4913-9d4f-1f8736efca32	profile-pictures/2026/06/20/7aae8094-c463-4ca7-b5f7-87f3a1d16ad0.jpg	Toma Marinescu	\N	1996-01-19	tommy82	Romania	0	USER	2026-06-20 18:33:49.746848	2026-06-20 18:33:49.746848	0	0	\N	\N	f	\N
b128e98a-984e-4c8f-a537-8c7d9b576a0c	f543d3f0-5da3-4d15-a976-febbb8bf0083	profile-pictures/2026/06/20/39cbe811-92b2-41c9-9266-4821dae75b7c.jpg	Mihnea Stoica	\N	1995-09-03	mihnea.gt	Romania	0	USER	2026-06-20 18:33:49.746848	2026-06-20 18:33:49.746848	0	0	\N	\N	f	\N
99fad2b1-fe37-4e18-b931-d28f56aaa267	e02522de-a128-4824-b014-375c96477f32	profile-pictures/2026/06/20/220b0491-3888-4db2-b72d-3e218516ec0f.jpg	Carina Dumitru	\N	1999-05-22	carina.k	Romania	0	USER	2026-06-20 18:33:49.746848	2026-06-20 18:33:49.746848	0	0	\N	\N	f	\N
39b9c09e-db8a-4e98-87f7-32836533cb86	7b027453-1ede-48ec-a942-b260d7c37494	profile-pictures/2026/06/20/0bd1899a-7b5c-42db-badd-96a1008ff3d4.jpg	Noemi Constantin	\N	2001-02-14	noemi.v	Romania	0	USER	2026-06-20 18:33:49.746848	2026-06-20 18:33:49.746848	0	0	\N	\N	f	\N
80d8e2f5-5f68-458d-a6d3-e6c27f595a06	beb61e4d-752f-4d36-9174-3e443862df55	profile-pictures/2026/06/20/18215234-ea70-400e-beb4-e48dd140be2e.jpg	Luca Serban	\N	1994-12-30	lucas.spots	Romania	0	USER	2026-06-20 18:33:49.746848	2026-06-20 18:33:49.746848	0	0	\N	\N	f	\N
7945b026-8727-4152-a3d8-7296f5a028fd	117991ef-d6b1-43b5-8e9d-ef8edf978574	profile-pictures/2026/06/20/42443493-4a6d-4980-b23a-4f0c53e964d1.jpg	Vlad Marin	\N	1998-08-17	vlad.m	Romania	0	USER	2026-06-20 18:33:49.746848	2026-06-20 18:33:49.746848	0	0	\N	\N	f	\N
b838b931-b37a-4761-b762-48a6a27407fd	8f3d29f4-9ff3-4f3e-8da5-c3c7571e9036	profile-pictures/2026/06/20/64750458-be3c-4cfc-8427-cfd4225e12be.jpg	Amara Preda	\N	1997-04-09	amara22	Romania	0	USER	2026-06-20 18:33:49.746848	2026-06-20 18:33:49.746848	0	0	\N	\N	f	\N
94077bb6-3204-4818-b312-536d6e88119d	2ec3c0e1-565e-44a7-811f-ce0ef0727b82	profile-pictures/2026/06/27/1ef906fd-c781-4ea0-9f47-9aaced097a76.jpg	snjsns	\N	2000-01-29	sjjsjs	Antigua & Barbuda	10	USER	2026-06-27 18:09:53.123012	2026-06-27 18:09:53.123012	1	1	2026-06-27	Europe/Bucharest	t	1
01a221f6-8491-4d35-a60c-fd5399bc194e	db686ac8-05a4-458e-9680-82c1ae8c14b4	profile-pictures/2026/06/27/28a4956c-df07-46d6-bc1b-a6dd8b6b2c77.jpg	porsche	\N	2000-01-30	porsche	Canada	0	USER	2026-06-27 19:19:44.856595	2026-06-27 19:19:44.856595	0	0	\N	\N	t	2
aea27264-e0e1-4981-9ffd-104046764c62	e77994e2-84c7-405e-bdfb-056009da2b51	profile-pictures/2026/06/20/b89c287d-1fa6-46ed-b2a1-33c3e2092141.jpg	Alex Popescu	\N	1998-03-12	alexp21	Romania	0	USER	2026-06-20 18:33:49.746848	2026-06-20 18:33:49.746848	2	2	2026-06-24	Europe/Bucharest	f	\N
ab55e2ee-9e0b-493b-9f90-a7bdbfe32f70	95596c63-670c-4eac-917c-fdcc5fab98e0	profile-pictures/2026/06/20/d81b9672-807a-41a4-bce6-6a6931e04671.jpg	Iustin Radu	\N	2000-11-05	iustin64	Romania	0	USER	2026-06-20 18:33:49.746848	2026-06-20 18:33:49.746848	1	1	2026-06-26	Europe/Bucharest	f	\N
\.


--
-- Data for Name: users_cars; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.users_cars (id, user_id, car_model_id, custom_brand, custom_model, image_path, created_at, updated_at) FROM stdin;
c190674e-3040-43c4-891d-2a04f75132fe	94077bb6-3204-4818-b312-536d6e88119d	56ac39ba-ef34-40e8-90fc-697b87a86a67	\N	\N	user-cars/2026/06/27/393dc7fe-ae46-45cc-8cda-7fef0aab6fb3.jpg	2026-06-27 18:09:53.585657	2026-06-27 18:09:53.585657
29d4f88b-7ec4-4251-a123-2403d36d7614	01a221f6-8491-4d35-a60c-fd5399bc194e	5e8dbc27-5976-4a23-aa8e-f87c01e65725	\N	\N	user-cars/2026/06/27/b669ae91-84b0-494f-ab09-2c581614db7b.jpg	2026-06-27 19:19:45.533047	2026-06-27 19:19:45.533047
\.


--
-- Name: auth_credentials auth_credentials_email_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auth_credentials
    ADD CONSTRAINT auth_credentials_email_key UNIQUE (email);


--
-- Name: auth_credentials auth_credentials_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auth_credentials
    ADD CONSTRAINT auth_credentials_pkey PRIMARY KEY (id);


--
-- Name: auth_sessions auth_sessions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auth_sessions
    ADD CONSTRAINT auth_sessions_pkey PRIMARY KEY (id);


--
-- Name: car_models car_models_brand_model_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.car_models
    ADD CONSTRAINT car_models_brand_model_key UNIQUE (brand, model);


--
-- Name: car_models car_models_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.car_models
    ADD CONSTRAINT car_models_pkey PRIMARY KEY (id);


--
-- Name: comments comments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.comments
    ADD CONSTRAINT comments_pkey PRIMARY KEY (id);


--
-- Name: early_spotter_counter early_spotter_counter_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.early_spotter_counter
    ADD CONSTRAINT early_spotter_counter_pkey PRIMARY KEY (id);


--
-- Name: flyway_schema_history flyway_schema_history_pk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.flyway_schema_history
    ADD CONSTRAINT flyway_schema_history_pk PRIMARY KEY (installed_rank);


--
-- Name: friend_requests friend_requests_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.friend_requests
    ADD CONSTRAINT friend_requests_pkey PRIMARY KEY (sender_id, receiver_id);


--
-- Name: friends friends_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.friends
    ADD CONSTRAINT friends_pkey PRIMARY KEY (user_id_1, user_id_2);


--
-- Name: leaderboard_rank_snapshots leaderboard_rank_snapshots_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.leaderboard_rank_snapshots
    ADD CONSTRAINT leaderboard_rank_snapshots_pkey PRIMARY KEY (snapshot_date, user_id);


--
-- Name: likes likes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.likes
    ADD CONSTRAINT likes_pkey PRIMARY KEY (id);


--
-- Name: likes likes_user_id_post_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.likes
    ADD CONSTRAINT likes_user_id_post_id_key UNIQUE (user_id, post_id);


--
-- Name: posts posts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.posts
    ADD CONSTRAINT posts_pkey PRIMARY KEY (id);


--
-- Name: reports reports_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reports
    ADD CONSTRAINT reports_pkey PRIMARY KEY (id);


--
-- Name: reports reports_reporter_id_post_id_reason_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reports
    ADD CONSTRAINT reports_reporter_id_post_id_reason_key UNIQUE (reporter_id, post_id, reason);


--
-- Name: users users_auth_credential_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_auth_credential_id_key UNIQUE (auth_credential_id);


--
-- Name: users_cars users_cars_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users_cars
    ADD CONSTRAINT users_cars_pkey PRIMARY KEY (id);


--
-- Name: users_cars users_cars_user_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users_cars
    ADD CONSTRAINT users_cars_user_id_key UNIQUE (user_id);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: flyway_schema_history_s_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX flyway_schema_history_s_idx ON public.flyway_schema_history USING btree (success);


--
-- Name: idx_comments_post_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_comments_post_created ON public.comments USING btree (post_id, created_at DESC);


--
-- Name: idx_comments_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_comments_user ON public.comments USING btree (user_id);


--
-- Name: idx_friend_requests_receiver; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_friend_requests_receiver ON public.friend_requests USING btree (receiver_id);


--
-- Name: idx_friends_user_2; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_friends_user_2 ON public.friends USING btree (user_id_2);


--
-- Name: idx_likes_post; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_likes_post ON public.likes USING btree (post_id);


--
-- Name: idx_posts_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_posts_created ON public.posts USING btree (created_at DESC);


--
-- Name: idx_posts_feed_keyset; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_posts_feed_keyset ON public.posts USING btree (created_at DESC, id DESC);


--
-- Name: idx_posts_user_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_posts_user_created ON public.posts USING btree (user_id, created_at DESC);


--
-- Name: idx_posts_user_created_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_posts_user_created_id ON public.posts USING btree (user_id, created_at DESC, id DESC);


--
-- Name: idx_posts_user_source_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_posts_user_source_created ON public.posts USING btree (user_id, source, created_at DESC);


--
-- Name: idx_rank_snapshots_user_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_rank_snapshots_user_date ON public.leaderboard_rank_snapshots USING btree (user_id, snapshot_date DESC);


--
-- Name: idx_reports_post; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reports_post ON public.reports USING btree (post_id);


--
-- Name: idx_sessions_credential; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sessions_credential ON public.auth_sessions USING btree (credential_id);


--
-- Name: idx_sessions_prev_hash; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sessions_prev_hash ON public.auth_sessions USING btree (prev_token_hash) WHERE (prev_token_hash IS NOT NULL);


--
-- Name: idx_users_username_lower; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX idx_users_username_lower ON public.users USING btree (lower((username)::text));


--
-- Name: uq_active_session_per_credential; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_active_session_per_credential ON public.auth_sessions USING btree (credential_id) WHERE ((status)::text = 'ACTIVE'::text);


--
-- Name: uq_sessions_refresh_hash; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_sessions_refresh_hash ON public.auth_sessions USING btree (refresh_token_hash);


--
-- Name: uq_users_early_spotter_number; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_users_early_spotter_number ON public.users USING btree (early_spotter_number) WHERE (early_spotter_number IS NOT NULL);


--
-- Name: posts set_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER set_updated_at BEFORE UPDATE ON public.posts FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();


--
-- Name: users_cars set_users_cars_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER set_users_cars_updated_at BEFORE UPDATE ON public.users_cars FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();


--
-- Name: auth_sessions auth_sessions_credential_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auth_sessions
    ADD CONSTRAINT auth_sessions_credential_id_fkey FOREIGN KEY (credential_id) REFERENCES public.auth_credentials(id) ON DELETE CASCADE;


--
-- Name: auth_sessions auth_sessions_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auth_sessions
    ADD CONSTRAINT auth_sessions_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: comments comments_post_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.comments
    ADD CONSTRAINT comments_post_id_fkey FOREIGN KEY (post_id) REFERENCES public.posts(id) ON DELETE CASCADE;


--
-- Name: comments comments_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.comments
    ADD CONSTRAINT comments_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: friend_requests friend_requests_receiver_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.friend_requests
    ADD CONSTRAINT friend_requests_receiver_id_fkey FOREIGN KEY (receiver_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: friend_requests friend_requests_sender_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.friend_requests
    ADD CONSTRAINT friend_requests_sender_id_fkey FOREIGN KEY (sender_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: friends friends_user_id_1_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.friends
    ADD CONSTRAINT friends_user_id_1_fkey FOREIGN KEY (user_id_1) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: friends friends_user_id_2_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.friends
    ADD CONSTRAINT friends_user_id_2_fkey FOREIGN KEY (user_id_2) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: leaderboard_rank_snapshots leaderboard_rank_snapshots_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.leaderboard_rank_snapshots
    ADD CONSTRAINT leaderboard_rank_snapshots_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: likes likes_post_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.likes
    ADD CONSTRAINT likes_post_id_fkey FOREIGN KEY (post_id) REFERENCES public.posts(id) ON DELETE CASCADE;


--
-- Name: likes likes_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.likes
    ADD CONSTRAINT likes_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: posts posts_car_model_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.posts
    ADD CONSTRAINT posts_car_model_id_fkey FOREIGN KEY (car_model_id) REFERENCES public.car_models(id) ON DELETE RESTRICT;


--
-- Name: posts posts_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.posts
    ADD CONSTRAINT posts_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: reports reports_post_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reports
    ADD CONSTRAINT reports_post_id_fkey FOREIGN KEY (post_id) REFERENCES public.posts(id) ON DELETE CASCADE;


--
-- Name: reports reports_reporter_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reports
    ADD CONSTRAINT reports_reporter_id_fkey FOREIGN KEY (reporter_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: users users_auth_credential_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_auth_credential_id_fkey FOREIGN KEY (auth_credential_id) REFERENCES public.auth_credentials(id) ON DELETE CASCADE;


--
-- Name: users_cars users_cars_car_model_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users_cars
    ADD CONSTRAINT users_cars_car_model_id_fkey FOREIGN KEY (car_model_id) REFERENCES public.car_models(id) ON DELETE RESTRICT;


--
-- Name: users_cars users_cars_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users_cars
    ADD CONSTRAINT users_cars_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- PostgreSQL database dump complete
--

\unrestrict PMKG2xYh0GhwufsyBtj68cmnN3IC3jDjN6Zwx14LO2OLdL11WgQaAAXC02dKeRO

