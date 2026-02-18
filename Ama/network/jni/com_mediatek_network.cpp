/* ---------------------------------------------------------------------------- *
 * Copyright Statement:                                                         *
 *                                                                              *
 * This software/firmware and related documentation ("MediaTek Software") are   *
 * protected under relevant copyright laws. The information contained herein is *
 * confidential and proprietary to MediaTek Inc. and/or its licensors. Without  *
 * the prior written permission of MediaTek inc. and/or its licensors, any      *
 * reproduction, modification, use or disclosure of MediaTek Software, and      *
 * information contained herein, in whole or in part, shall be strictly         *
 * prohibited.                                                                  *
 *                                                                              *
 * Copyright  (C) [2021]  MediaTek Inc. All rights reserved.                    *
 *                                                                              *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES  *
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")      *
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER   *
 * ON AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL           *
 * WARRANTIES, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED     *
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR           *
 * NONINFRINGEMENT. NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH  *
 * RESPECT TO THE SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY,             *
 * INCORPORATED IN, OR SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES *
 * TO LOOK ONLY TO SUCH THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO.    *
 * RECEIVER EXPRESSLY ACKNOWLEDGES THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO *
 * OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES CONTAINED IN MEDIATEK        *
 * SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK SOFTWARE   *
 * RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR      *
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S  *
 * ENTIRE AND CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE        *
 * RELEASED HEREUNDER WILL BE, AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE   *
 * MEDIATEK SOFTWARE AT ISSUE, OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE   *
 * CHARGE PAID BY RECEIVER TO MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.     *
 *                                                                              *
 * The following software/firmware and/or related documentation ("MediaTek      *
 * Software") have been modified by MediaTek Inc. All revisions are subject to  *
 * any receiver's applicable license agreements with MediaTek Inc.              *
 * ---------------------------------------------------------------------------- */

#include <jni.h>
#include <poll.h>
#include <fcntl.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <stdarg.h>
#include <unistd.h>
#include <net/if.h>
#include <sys/ioctl.h>
#include <sys/socket.h>
#include <linux/sockios.h>
#include <log/log.h>
#include <stdio.h>
#include "libnetpriv.h"

#define LOG_TAG "MtkNetworkManagerJNI"

#define JNIREG_CLASS "com/mediatek/net/MtkNetworkNative"
#define IWPRIV_BIN "/system/vendor/bin/iwpriv"

#define SIOC_SET_WOP_IPV6_CMD (SIOCDEVPRIVATE+10)
#define SIOC_SET_WOP_CMD (SIOCDEVPRIVATE+11)
#define SIOC_CLR_WOP_CMD (SIOCDEVPRIVATE+12)
#define SIOC_SET_WOL_CMD (SIOCDEVPRIVATE+13)
#define SIOC_GET_WOL_CMD (SIOCDEVPRIVATE+14)

#define PROTOCOL_TCP (6)
#define PROTOCOL_UDP (17)
#define MDNS_UDP_PORT (5353)

#define MAX_NUM (20)
#define MAX_COMMAND_LEN (50)
#define MAX_WAKEUP_REASON_LEN (10)
#define LAN_WAKEUP_REASON_LEN (3)
#define CAST_WAKEUP_REASON_LEN (4)
#define POLL_DURATION (200*1000)
#define STRERR_BUF_LEN (100)

struct ioctl_wop_para_cmd {
    unsigned char protocol_type;
    unsigned char port_count;
    unsigned int *port_array;
};

struct ioctl_wol_para_cmd {
    bool is_enable_wol;
};

static int fd = -1;

#define NETPRIV_ARGC_NUM 3
int net_exec(char* command)
{
    ALOGD("net_exec command =%s",command);
    char *argv[] = {"wlan0", "driver", command, NULL};
    int ret = 0;

    ret = netpriv(NETPRIV_ARGC_NUM, argv);
    ALOGD("net_exec return %d",ret);
    return 0;
}

static int execute_ethernet_ioctl_command(const char *name, int command, void *user_data) {
    int ctl_sock = -1;
    struct ifreq ifr;
    int ret = 0;

    if (ctl_sock == -1) {
        ctl_sock = socket(AF_INET, SOCK_DGRAM, 0);
        if (ctl_sock < 0) {
            switch (command) {
                case SIOC_SET_WOP_IPV6_CMD:
                    ALOGE("Set Ethernet WOP_IPV6 listen port create socket failed");
                break;
                case SIOC_SET_WOP_CMD:
                    ALOGE("Set Ethernet WOC listen port create socket failed");
                break;
                case SIOC_CLR_WOP_CMD:
                    ALOGE("Clear Ethernet WOC listen port create socket failed");
                break;
                case SIOC_SET_WOL_CMD:
                    ALOGE("Set Ethernet WOL enable/disable create socket failed");
                break;
                case SIOC_GET_WOL_CMD:
                    ALOGE("Get Ethernet WOL enable/disable create socket failed");
                break;
                default:
                    ALOGE("Unknown ioctl command");
                break;
            }
            return -1;
        }
    }

    memset(&ifr, 0, sizeof(struct ifreq));
    strncpy(ifr.ifr_name, name, IFNAMSIZ);
    ifr.ifr_name[IFNAMSIZ - 1] = 0;

    ifr.ifr_data = user_data;
    ret = ioctl(ctl_sock, command, &ifr);
    switch (command) {
        case SIOC_SET_WOP_IPV6_CMD:
            ALOGD("Set Ethernet WOP_IPV6 listen port, ret = %d\n", ret);
        break;
        case SIOC_SET_WOP_CMD:
            ALOGD("Set Ethernet WOC listen port, ret = %d\n", ret);
        break;
        case SIOC_CLR_WOP_CMD:
            ALOGD("Clear Ethernet WOC listen port, ret = %d\n", ret);
        break;
        case SIOC_SET_WOL_CMD:
            ALOGD("Set Ethernet WOL enable/disable, ret = %d\n", ret);
        break;
        case SIOC_GET_WOL_CMD:
            ALOGD("Get Ethernet WOL enable/disable, ret = %d\n", ret);
        break;
        default:
            ALOGD("Unknown ioctl command");
        break;
    }
    if (ctl_sock != -1) {
        (void)close(ctl_sock);
        ctl_sock = -1;
    }

    return ret;
}

/*
* This function used to enable or disable the WoWL function.
*WoWL: Wake over WLAN
*@param ifname, interface name wlan0..
*@param : 0, disalbe , 1 ,enable
*@return : true, operation success, other : fail.
*/

JNIEXPORT jboolean JNICALL native_wifi_set_wowl(JNIEnv *env, jclass clazz, jboolean enable) {
    int i4_ret = 0;

    ALOGD("native_wifi_set_wowl");

    if (enable) {
        i4_ret = net_exec("set_wow_enable 1");
    } else {
        i4_ret = net_exec("set_wow_enable 0");
    }
    ALOGD("wifi setWOWEnabled driver return %d \n", i4_ret);
    return (jboolean)(i4_ret == 0 ? 1 : 0);
}

JNIEXPORT jboolean JNICALL native_set_wopacket(JNIEnv *env, jclass clazz, jboolean enable) {
    ALOGD("native_set_wopacket");
    return (jboolean)1;
}

JNIEXPORT jboolean JNICALL native_set_wifi_ps_awake(JNIEnv *env, jclass clazz, jboolean enable) {
    int i4_ret = 0;

    ALOGD("native_set_wifi_ps_awake");

    if (enable) {
        i4_ret = net_exec("set_adv_pws 1");
    } else {
        i4_ret = net_exec("set_adv_pws 0");
    }
    ALOGD("wifi setPsAwakeEnabled driver return %d \n", i4_ret);
    return (jboolean)(i4_ret == 0 ? 1 : 0);
}

JNIEXPORT jboolean JNICALL native_set_wifi_csa(JNIEnv *env, jclass clazz, jboolean enable) {
    ALOGD("native_set_wifi_csa");

    ALOGD("wifi setCSAEnabled 7668 driver unsupport \n");
    return (jboolean)0;
}

JNIEXPORT jboolean JNICALL native_set_wifi_ps_awake_interval(JNIEnv *env, jclass clazz, jint interval) {
    int i4_ret = 0;
    char s_cmd[MAX_COMMAND_LEN] = {0};

    ALOGD("native_set_wifi_ps_awake_interval");
    snprintf(s_cmd, sizeof(s_cmd), "set_mdtim %d", interval);

    i4_ret = net_exec(s_cmd);
    ALOGD("wifi setPsAwakeInterval driver return %d \n", i4_ret);
    return (jboolean)(i4_ret == 0 ? 1 : 0);
}

JNIEXPORT jboolean JNICALL native_set_wopacket_listen_port(JNIEnv *env, jclass clazz) {
    int ret = 0;
    int n = 0;
    unsigned int eth_udp_port[MAX_NUM + 1] = {0};
    unsigned char udp_count_idx = 0;
    struct ioctl_wop_para_cmd eth_wopacket_para;

    ALOGD("native_set_wopacket_listen_port");

    ret = net_exec("set_wow_tcp 0 8008,8009,1900"); //wifi tcp v4 port
    ALOGD("(WOWL)wifi set tcp4 port driver return %d \n", ret);

    ret = net_exec("set_wow_udp 0 5353"); //wifi udp v4 port
    ALOGD("(WOWL)wifi set udp4 port driver return %d \n", ret);

    ret = execute_ethernet_ioctl_command("eth0", SIOC_CLR_WOP_CMD, NULL); // Clear Ethernet WOC listen port
    if (ret < 0) {
       ALOGD("Failed to clear listen port for Ethernet WOC");
    }

    eth_udp_port[udp_count_idx++] = MDNS_UDP_PORT;

    eth_wopacket_para.protocol_type = PROTOCOL_UDP;
    eth_wopacket_para.port_count = udp_count_idx;
    eth_wopacket_para.port_array = eth_udp_port;

    for (n = 0; n < udp_count_idx; n ++) {
        ALOGD("Ethernet UDP listen port%d is %d", n, eth_wopacket_para.port_array[n]);
    }

    ret = execute_ethernet_ioctl_command("eth0", SIOC_SET_WOP_CMD, &eth_wopacket_para); // Set Ethernet WOC UDP listen port
    if (ret < 0) {
        ALOGD("Failed to set UDP listen port for Ethernet WOC");
    }

    if (ret < 0) {
        return (jboolean)0;
    } else {
        return (jboolean)1;
    }
}

JNIEXPORT jboolean JNICALL native_clear_wopacket_listen_port(JNIEnv *env, jclass clazz) {
    int ret = 0;

    ALOGD("native_clear_wopacket_listen_port");

    ret = net_exec("set_wow_tcp 0");
    ALOGD("(WOWL)wifi clear tcp4 port driver return %d \n", ret);

    ret = net_exec("set_wow_udp 0");
    ALOGD("(WOWL)wifi clear udp4 port driver return %d \n", ret);

    ret = net_exec("set_wow_tcp 1");
    ALOGD("(WOWL)wifi clear tcp6 port driver return %d \n", ret);

    ret = net_exec("set_wow_udp 1");
    ALOGD("(WOWL)wifi clear udp6 port driver return %d \n", ret);

    ret = execute_ethernet_ioctl_command("eth0", SIOC_CLR_WOP_CMD, NULL); // Clear Ethernet WOC listen port
    if (ret < 0) {
        ALOGD("Failed to clear listen port for Ethernet WOC");
    }

    if (ret < 0) {
        return (jboolean)0;
    } else {
        return (jboolean)1;
    }
}

JNIEXPORT jboolean JNICALL native_set_enable_ethernet_wol(JNIEnv *env, jclass clazz, jboolean enable) {
    int i4_ret = 0;
    struct ioctl_wol_para_cmd eth_wol_para;

    ALOGD("native_set_enable_ethernet_wol");

    if (enable) {
        eth_wol_para.is_enable_wol = true;
    } else {
        eth_wol_para.is_enable_wol = false;
    }

    i4_ret = execute_ethernet_ioctl_command("eth0", SIOC_SET_WOL_CMD, &eth_wol_para); // Set Ethernet WOL enable/disable
    if (i4_ret < 0) {
        ALOGD("Failed to set enable/disable for Ethernet WOL");
    }

    if (i4_ret < 0) {
        return (jboolean)0;
    } else {
        return (jboolean)1;
    }
}

JNIEXPORT jboolean JNICALL native_is_ethernet_wol_enabled(JNIEnv *env, jclass clazz) {
    int ret = 0;
    struct ioctl_wol_para_cmd eth_wol_para;

    ALOGD("native_is_ethernet_wol_enabled");

    ret = execute_ethernet_ioctl_command("eth0", SIOC_GET_WOL_CMD, &eth_wol_para);
    if (ret < 0) {
        ALOGE("Failed to get enable/disable for Ethernet WOL");
        return (jboolean)0;
    }
    ALOGD("eth_wol_para.is_enable_wol = %d", eth_wol_para.is_enable_wol);

    if (eth_wol_para.is_enable_wol) {
        return (jboolean)1;
    } else {
        return (jboolean)0;
    }
}

JNIEXPORT jstring JNICALL native_start_monitor_network_wake_up(JNIEnv *env, jclass clazz, jstring iface) {
    jstring wakeup_reason = nullptr;
    struct pollfd fds;
    char reason[MAX_WAKEUP_REASON_LEN] = {0};
    char error_buf[STRERR_BUF_LEN]={0};
    int ret = 0;
    int length = 0;

    ALOGD("native_start_monitor_network_wake_up");

    if (fd == -1) {
        const char *interface = env->GetStringUTFChars(iface, nullptr);
        ALOGD("interface = %s", interface);
        if (strncmp("eth0", interface, strlen(interface)) == 0) {
            fd = open("/sys/class/net/eth0/device/mtk_dbg/wakeup_reason", O_RDONLY);
        }
        env->ReleaseStringUTFChars(iface, interface);
    }
    ALOGD("fd = %d", fd);
    if (fd <= 0) {
        (void)strerror_r(errno, error_buf, sizeof(error_buf));
        ALOGE("errno = %d, error = %s", errno, error_buf);
    } else {
        length = read(fd, reason, 0);
        ALOGD("length = %d, reason = %s", length, reason);
        if (length == 0) {
            ALOGD("This is dummy read. Don't care it.");
        }
    }

    fds.fd = fd;
    fds.events = POLLPRI;
    fds.revents = 0;

    while (fd > 0) {
        fds.revents = 0;
        ret = poll(&fds, 1, -1);
        if (ret > 0 && (fds.revents & POLLPRI)) {
            length = read(fd, reason, MAX_WAKEUP_REASON_LEN);
            ALOGD("ret = %d, fds.revents = %d, length = %d, reason = %s", ret, fds.revents, length, reason);
            if ((length > 0)
                && (strncmp("lan", reason, LAN_WAKEUP_REASON_LEN) == 0
                || strncmp("cast", reason, CAST_WAKEUP_REASON_LEN) == 0)) {
                wakeup_reason = env->NewStringUTF(reason);
                memset(reason, 0, sizeof(reason));
                break;
            }
        }
        usleep(POLL_DURATION);
    }

    if (fd != -1) {
        close(fd);
        fd = -1;
    }

    return wakeup_reason;
}

JNIEXPORT jboolean JNICALL native_stop_monitor_network_wake_up(JNIEnv *env, jclass clazz) {
    ALOGD("native_stop_monitor_network_wake_up");

    ALOGD("fd = %d", fd);
    if (fd != -1) {
        close(fd);
        fd = -1;
    }

    return (jboolean)1;
}

#define MAX_PORTS_TYPE 3
#define MAX_CMD_LENGTH 512
#define MAX_SINGLE_LINE 256
#define MAX_SINGLE_LEN 10
#define MAX_ITEM 12
#define MAX_ITEM_LEN 64
#define TCP4 0
#define TCP6 1
#define UDP 2
#define SPACE 0x20

const char s_tcp_file_path[] = "/proc/net/tcp";
const char s_tcp6_file_path[] = "/proc/net/tcp6";

typedef struct recv_listen_ports {
    unsigned int udp_local_port[MAX_NUM + 1];
    unsigned int tcp_local_port[MAX_NUM + 1];
    unsigned int tcp_remote_port[MAX_NUM + 1];
    unsigned int udp_local_len;
    unsigned int tcp_local_len;
    unsigned int tcp_remote_len;
} ports_listen_t;

typedef struct search_ports_param {
    unsigned int local_port[MAX_NUM + 1];
    unsigned int remote_port[MAX_NUM + 1];
    unsigned int ex_ipv4_port[MAX_NUM + 1];
    unsigned int remote_port_len;
    unsigned int ex_ipv4_len;
} ports_search_t;

typedef struct set_ports_cmd {
    unsigned int ports_len[MAX_PORTS_TYPE];
    unsigned int ports_num[MAX_PORTS_TYPE][MAX_NUM + 1];
} ports_set_t;

static char *remove_multi_blank(char *src, char *desc) {
    char *ptr = src;
    while (*ptr == ' ')
        ptr++;
    src = ptr;
    ptr = desc;
    while (*src != '\0') {
        if (*src != SPACE || (*src == SPACE && *(src + 1) != SPACE)) {
            *desc++ = *src++;
        } else {
            src++;
        }
    }
    *desc = '\0';
    return ptr;
}


static void parseLine(char *s_line, char ss_result[][MAX_ITEM_LEN]) {
    unsigned int i = 0;
    char desc[MAX_SINGLE_LINE] = {0};
    char *ps_temp = NULL;
    char *ps_item = NULL;
    char *ps_out_temp = NULL;
    char *delim = " ";

    s_line[strlen(s_line) - 1] = '\0';
    ps_temp = remove_multi_blank(s_line, desc);
    ps_item = strtok_r(ps_temp, delim, &ps_out_temp);
    while (ps_item != NULL) {
        if (i >= MAX_ITEM)
            break;
        if (MAX_ITEM_LEN < strlen(ps_item)) {
            ALOGE("exceed max item len, max = %d  error is %d", MAX_ITEM_LEN, strlen(ps_item));
            break;
        }
        strncpy(ss_result[i], ps_item, sizeof(ss_result[i]) - 1);
        ps_item = strtok_r(NULL, delim, &ps_out_temp);
        i++;
    }
}

static unsigned int parseRemotePort(char *rem_address) {
    char *ps_temp = strstr(rem_address, ":");
    int ret = 0;
    if (ps_temp == NULL) {
        ALOGE("rem_address is null");
        return -1;
    }
    ps_temp++;
    unsigned int x = 0;
    ret = sscanf(ps_temp, "%x", &x);
    if (ret < 0) {
        ALOGE("sscanf error");
        return 0;
    }
    return x;
}

static int match_remote_port(unsigned int remote_port[],
                             unsigned int remote_len,
                             unsigned int result_port) {
    int result = -1;
    for (int i = 0; i < remote_len; i++) {
        if (remote_port[i] == result_port) {
            result = 0;
            break;
        }
    }
    return result;
}

static unsigned int net_get_tcp_local_port(const char *s_file, ports_search_t *srch) {
    FILE *fp = NULL;
    char s_line[256] = {0};
    unsigned int i = 0;
    unsigned int j = 0;
    int ret = 0;

    fp = fopen(s_file, "rt");
    if (fp == NULL) {
        ALOGD("open %s failed", s_file);
        return 0;
    }
    while (fgets(s_line, 255, fp) != NULL) {
        char s_line_result[MAX_ITEM][MAX_ITEM_LEN] = {{0}};
        unsigned int remote_port_temp = 0;
        unsigned int local_port_temp = 0;

        parseLine(s_line, s_line_result);
        if (strncmp(s_line_result[3], "01", 2) !=0) {
            continue;
        }
        if (i >= MAX_NUM || j >= MAX_NUM) {
            ALOGD("only support max = %d tcp ports!", MAX_NUM);
            break;
        }
        remote_port_temp = parseRemotePort(s_line_result[2]);
        if (remote_port_temp > 0) {
            if (match_remote_port(srch->remote_port, srch->remote_port_len, remote_port_temp) == 0) {
                local_port_temp = parseRemotePort(s_line_result[1]);
                if (local_port_temp > 0) {
                    if (strncmp(s_line_result[2], "00000000000000000000FFFF", 24) == 0 ||
                        strncmp(s_line_result[2], "0000000000000000FFFF", 20) == 0) {
                        srch->ex_ipv4_port[j++] = local_port_temp;
                        ALOGD("add valid local ex_ipv4 port : %d", local_port_temp);
                    } else {
                    srch->local_port[i++] = local_port_temp;
                    ALOGD("add valid local tcp/tcp6 port : %d", local_port_temp);
                    }
                }
            }
        }
    }
    srch->ex_ipv4_len = j;
    ret = fclose(fp);
    if (ret < 0) {
        ALOGE("fclose error");
    }
    return i;
}

static int wlan_set_ports(ports_set_t *ports_set) {
    int ret = 0;
    unsigned int max_cat = 0;
    ports_set_t ports;
    memset(&ports, 0, sizeof(ports_set_t));
    memcpy(&ports, ports_set, sizeof(ports_set_t));
    for (int i = 0; i < MAX_PORTS_TYPE; i++) {
        char setportcmd[MAX_CMD_LENGTH] = {0};
        char str_temp[MAX_SINGLE_LEN] = {0};
        switch (i) {
            case TCP4:
                ret = snprintf(setportcmd, MAX_CMD_LENGTH, "set_wow_tcp 0 ");
                if (ret < 0) {
                    ALOGD("set_wow_tcp 0 snprintf transform error");
                }
                break;
            case TCP6:
                ret = snprintf(setportcmd, MAX_CMD_LENGTH, "set_wow_tcp 1 ");
                if (ret < 0) {
                    ALOGD("set_wow_tcp 1 snprintf transform error");
                }
                break;
            case UDP:
                ret = snprintf(setportcmd, MAX_CMD_LENGTH, "set_wow_udp 0 ");
                if (ret < 0) {
                    ALOGD("set_wow_udp 0 snprintf transform error");
                }
                break;
            default:
                ALOGD("unknow port type");
                return -1;
        }
        for (int j = 0; j < ports.ports_len[i]; j++) {
            if (ports.ports_num[i][j] > 0) {
                ret = snprintf(str_temp, MAX_SINGLE_LEN, "%d,", ports.ports_num[i][j]);
                if (ret < 0) {
                    ALOGD("snprintf transform error");
                } else {
                    max_cat = MAX_CMD_LENGTH - strlen(setportcmd) -1;
                    strncat(setportcmd, str_temp, max_cat);
                    if (strlen(setportcmd) >= MAX_CMD_LENGTH - 1) {
                        ALOGD("strncat transform error");
                        return -1;
                    }
                }
            }
        }
        setportcmd[strlen(setportcmd) - 1] = '\0';
        ret = net_exec(setportcmd);
        ALOGD("wifi set type %d port driver return %d \n", i, ret);
    }
    return 0;
}

static int lan_set_ports(ports_set_t *ports_set) {
    unsigned int eth_udp_port[MAX_NUM + 1] = {0};
    unsigned int eth_tcp_port[MAX_NUM + 1] = {0};
    unsigned int eth_tcpv6_port[MAX_NUM + 1] = {0};
    unsigned char count_idx_udp = 0;
    unsigned char count_idx_tcp = 0;
    unsigned char count_idx_tcp_v6 = 0;
    ports_set_t ports;
    struct ioctl_wop_para_cmd eth_wopacket_para;
    int ret = 0;

    memset(&ports, 0, sizeof(ports_set_t));
    memcpy(&ports, ports_set, sizeof(ports_set_t));
    ret = execute_ethernet_ioctl_command("eth0", SIOC_CLR_WOP_CMD, NULL);
    if (ret < 0) {
        ALOGD("Failed to clear listen port for Ethernet");
    }
    memset(&eth_wopacket_para, 0, sizeof(struct ioctl_wop_para_cmd));
    for (int j = 0; j < ports.ports_len[UDP]; j++) {
        if (count_idx_udp >= MAX_NUM) {
            break;
        }
        eth_udp_port[count_idx_udp++] = ports.ports_num[UDP][j];
    }
    if (count_idx_udp > 0) {
        eth_wopacket_para.protocol_type = PROTOCOL_UDP;
        eth_wopacket_para.port_count = count_idx_udp;
        eth_wopacket_para.port_array = eth_udp_port;
        for (int n = 0; n < count_idx_udp; n++) {
            ALOGD("Ethernet UDP listen port%d is %d", n, eth_wopacket_para.port_array[n]);
        }
        ret = execute_ethernet_ioctl_command("eth0", SIOC_SET_WOP_CMD, &eth_wopacket_para);
        if (ret < 0) {
            ALOGD("Failed to set UDP listen port for Ethernet");
        }
    } else {
        ALOGD("NO UDP listen port need to be set for Ethernet");
    }
    if (count_idx_udp < MAX_NUM) {
        memset(&eth_wopacket_para, 0, sizeof(struct ioctl_wop_para_cmd));
        for (int j = 0; j < ports.ports_len[TCP4]; j++) {
            if (count_idx_tcp >= MAX_NUM - count_idx_udp) {
                break;
            }
            eth_tcp_port[count_idx_tcp++] = ports.ports_num[TCP4][j];
        }
        if (count_idx_tcp > 0) {
            eth_wopacket_para.protocol_type = PROTOCOL_TCP;
            eth_wopacket_para.port_count = count_idx_tcp;
            eth_wopacket_para.port_array = eth_tcp_port;
            for (int n = 0; n < count_idx_tcp; n++) {
                ALOGD("Ethernet tcpv4 listen port%d is %d", n, eth_wopacket_para.port_array[n]);
            }
            ret = execute_ethernet_ioctl_command("eth0", SIOC_SET_WOP_CMD, &eth_wopacket_para);
            if (ret < 0) {
                ALOGD("Failed to set TCP listen port for Ethernet");
            }
        } else {
            ALOGD("no tvpv4 listen port need to be set for Ethernet");
        }
        if (count_idx_tcp + count_idx_udp < MAX_NUM) {
            memset(&eth_wopacket_para, 0, sizeof(struct ioctl_wop_para_cmd));
            for (int j = 0; j < ports.ports_len[TCP6]; j++) {
                if (count_idx_tcp_v6 + count_idx_tcp + count_idx_udp >= MAX_NUM) {
                    break;
                }
                eth_tcpv6_port[count_idx_tcp_v6++] = ports.ports_num[TCP6][j];
            }
            if (count_idx_tcp_v6 > 0) {
                eth_wopacket_para.protocol_type = PROTOCOL_TCP;
                eth_wopacket_para.port_count = count_idx_tcp_v6;
                eth_wopacket_para.port_array = eth_tcpv6_port;
                for (int n = 0; n < count_idx_tcp_v6; n++) {
                    ALOGD("Ethernet tcpv6 listen port%d is %d", n, eth_wopacket_para.port_array[n]);
                }
                ret = execute_ethernet_ioctl_command("eth0", SIOC_SET_WOP_IPV6_CMD, &eth_wopacket_para);
                if (ret < 0) {
                    ALOGD("Failed to set TCPv6 listen port for Ethernet");
                }
            }
        } else {
            ALOGD("no more ports for tvpv6 listen port");
        }
    } else {
        ALOGD("udp port is full, no more ports for Ethernet");
    }
    return ret;
}


JNIEXPORT jboolean JNICALL native_set_wakeup_listen_port_wake(
    JNIEnv *env, jobject obj, jintArray udp_local, jintArray tcp_local, jintArray tcp_remote) {
    ports_listen_t rcv_port;
    ports_search_t srch_port;
    ports_set_t set_port;
    unsigned int temp_len = 0;
    unsigned int set_ex_ipv4 = 0;
    int ret = 0;

    ALOGD("gc_debug native_set_wakeup_listen_port_wake");
    if (udp_local == NULL || tcp_local == NULL || tcp_remote == NULL) {
        ALOGE("get udp_local_port or tcp_remote_port error");
        return (jboolean)0;
    }
    rcv_port.udp_local_len = env->GetArrayLength(udp_local);
    rcv_port.tcp_local_len = env->GetArrayLength(tcp_local);
    rcv_port.tcp_remote_len = env->GetArrayLength(tcp_remote);

    env->GetIntArrayRegion(udp_local, 0, rcv_port.udp_local_len, reinterpret_cast<jint *>(rcv_port.udp_local_port));
    env->GetIntArrayRegion(tcp_local, 0, rcv_port.tcp_local_len, reinterpret_cast<jint *>(rcv_port.tcp_local_port));
    env->GetIntArrayRegion(tcp_remote, 0, rcv_port.tcp_remote_len, reinterpret_cast<jint *>(rcv_port.tcp_remote_port));

    memset(&set_port, 0, sizeof(ports_set_t));
    if (rcv_port.udp_local_len > 0 && rcv_port.udp_local_len <= MAX_NUM) {
        set_port.ports_len[UDP] = rcv_port.udp_local_len;
        memcpy(set_port.ports_num[UDP], rcv_port.udp_local_port, rcv_port.udp_local_len * sizeof(unsigned int));
        if (rcv_port.tcp_local_len > 0 && rcv_port.tcp_local_len <= MAX_NUM) {
            set_port.ports_len[TCP4] = rcv_port.tcp_local_len;
            memcpy(set_port.ports_num[TCP4], rcv_port.tcp_local_port, rcv_port.tcp_local_len * sizeof(unsigned int));
        }
    }

    if (rcv_port.tcp_remote_len > 0) {
        memset(&srch_port, 0, sizeof(ports_search_t));
        memcpy(srch_port.remote_port, rcv_port.tcp_remote_port, MAX_NUM * sizeof(unsigned int));
        srch_port.remote_port_len = rcv_port.tcp_remote_len;
        temp_len = net_get_tcp_local_port(s_tcp6_file_path, &srch_port);
        if (temp_len > MAX_NUM) {
            set_port.ports_len[TCP6] = MAX_NUM;
        } else {
            set_port.ports_len[TCP6] = temp_len;
        }
        memcpy(set_port.ports_num[TCP6], srch_port.local_port, MAX_NUM * sizeof(unsigned int));
        if (srch_port.ex_ipv4_len > 0) {
            if (set_port.ports_len[TCP4] + srch_port.ex_ipv4_len <= MAX_NUM) {
                memcpy(set_port.ports_num[TCP4] + set_port.ports_len[TCP4], srch_port.ex_ipv4_port, srch_port.ex_ipv4_len * sizeof(unsigned int));
                set_port.ports_len[TCP4] += srch_port.ex_ipv4_len;
            } else {
                memcpy(set_port.ports_num[TCP4] + set_port.ports_len[TCP4], srch_port.ex_ipv4_port, (MAX_NUM - set_port.ports_len[TCP4]) * sizeof(unsigned int));
                set_port.ports_len[TCP4] = MAX_NUM;
            }
        }
        if (set_port.ports_len[TCP4] < MAX_NUM) {
            memset(&srch_port, 0, sizeof(ports_search_t));
            memcpy(srch_port.remote_port, rcv_port.tcp_remote_port, MAX_NUM * sizeof(unsigned int));
            srch_port.remote_port_len = rcv_port.tcp_remote_len;
            temp_len = 0;
            temp_len = net_get_tcp_local_port(s_tcp_file_path, &srch_port);
            if (temp_len > 0) {
                if (set_port.ports_len[TCP4] + temp_len <= MAX_NUM) {
                    memcpy(set_port.ports_num[TCP4] + set_port.ports_len[TCP4], srch_port.local_port, temp_len * sizeof(unsigned int));
                    set_port.ports_len[TCP4] += temp_len;
                } else {
                    memcpy(set_port.ports_num[TCP4] + set_port.ports_len[TCP4], srch_port.local_port, (MAX_NUM - set_port.ports_len[TCP4]) * sizeof(unsigned int));
                    set_port.ports_len[TCP4] = MAX_NUM;
                }
            }
        }
    }
    ALOGD("tcp4port len is %d, tcp6port len is %d", set_port.ports_len[TCP4], set_port.ports_len[TCP6]);
    wlan_set_ports(&set_port);
    ret = lan_set_ports(&set_port);
    if (ret < 0) {
        return (jboolean)0;
    } else {
        return (jboolean)1;
    }
}


static JNINativeMethod gMethods[] = {
    { "wifiNativeSetWowl", "(Z)Z", reinterpret_cast<void *>(native_wifi_set_wowl)},
    { "setWoPacketNative", "(Z)Z", reinterpret_cast<void *>(native_set_wopacket)},
    { "setEnableWifiPsAwakeNative", "(Z)Z", reinterpret_cast<void *>(native_set_wifi_ps_awake)},
    { "setEnableWifiCSANative", "(Z)Z", reinterpret_cast<void *>(native_set_wifi_csa)},
    { "setWifiPsAwakeIntervalNative", "(I)Z", reinterpret_cast<void *>(native_set_wifi_ps_awake_interval)},
    { "setWoPacketListenPortNative", "()Z", reinterpret_cast<void *>(native_set_wopacket_listen_port)},
    { "clearWoPacketListenPortNative", "()Z", reinterpret_cast<void *>(native_clear_wopacket_listen_port)},
    { "setEnableEthernetWolNative", "(Z)Z", reinterpret_cast<void *>(native_set_enable_ethernet_wol)},
    { "isEthernetWolEnabledNative", "()Z", reinterpret_cast<void *>(native_is_ethernet_wol_enabled)},
    { "startMonitorNetworkWakeUpNative", "(Ljava/lang/String;)Ljava/lang/String;", reinterpret_cast<void *>(native_start_monitor_network_wake_up)},
    { "stopMonitorNetworkWakeUpNative", "()Z", reinterpret_cast<void *>(native_stop_monitor_network_wake_up)},
    { "setPacketListenPortNative", "([I[I[I)Z", reinterpret_cast<void *>(native_set_wakeup_listen_port_wake)},
};

int register_com_mediatek_networkNative(JNIEnv* env) {
    jclass clazz;

    clazz = env->FindClass(JNIREG_CLASS);
    if (clazz == NULL) {
        ALOGE("Native registration unable to find class '%s'", JNIREG_CLASS);
        return JNI_FALSE;
    }
    if (env->RegisterNatives(clazz, gMethods, sizeof(gMethods) / sizeof(gMethods[0])) < 0) {
        ALOGE("RegisterNatives failed for '%s'", JNIREG_CLASS);
        return JNI_FALSE;
    }
    return JNI_TRUE;
}
