/**
 * @author by keray
 * date:2019/8/5 17:04
 */
class Fetch {
    static request(input, error) {
        return window.fetch(input.url, {
            credentials: 'include',
            ...input
        })
            .then(r => {
                // 302重定向登录处理
                if (/\/login/g.test(r.url)) {
                    location.href = r.url;
                    return Promise.reject();
                }
                return r;
            })
            .then((r) => this.json(r))
            .then(r => !error ? this.check(r) : r);
    }


    static get(input, param, error) {
        if (typeof input === typeof '') {
            input = param ? input + "?" : input;
            if (param) {
                for (let key in param) {
                    if (param[key] === undefined || param[key] === null || param[key] === '') {
                        continue;
                    }
                    input += `${key}=${param[key]}&`
                }
                input = input.substring(0, input.length - 1);
            }
        } else {
            input.url = param ? input.url + "?" : input.url;
            if (param) {
                for (let key in param) {
                    input.url += `${key}=${param[key]}&`
                }
                input.url = input.url.substring(0, input.url.length - 1);
            }
        }

        input = input.url ? input : {
            url: input
        };
        return this.request({
            method: 'get',
            ...input,
        }, error);
    }

    static post(input, param, method, error) {
        let formData = new FormData();
        for (let key in param) {
            if (param[key] === undefined || param[key] === null || param[key] === '') {
                continue;
            }
            formData.append(key, param[key]);
        }
        input = input.url ? input : {
            url: input
        };
        return this.request({
            method: method ? method : 'POST',
            body: formData,
            ...input
        }, error);
    }

    static postJson(input, data, error) {
        input = input.url ? input : {
            url: input
        };
        return this.request({
            method: 'POST',
            body: JSON.stringify(data),
            headers: {
                'Content-Type': 'application/json'
            },
            ...input
        }, error);
    }

    static put(input, param, error) {
        return this.post(input, param, 'PUT', error);
    }

    static delete(input, param, error) {
        return this.post(input, param, 'DELETE', error);
    }

    static progress(input, param = {}, onProgress) {
        let formData = new FormData();
        for (let key in param) {
            if (param[key] === undefined || param[key] === null) {
                continue;
            }
            formData.append(key, param[key]);
        }
        let opts = {
            body: formData,
            ...input
        };
        return new Promise((resolve, reject) => {
            let xhr = new XMLHttpRequest();
            xhr.open(opts.method || 'POST', input.url || input);
            for (let key in opts.headers || {}) {
                xhr.setRequestHeader(key, opts.headers[key]);
            }
            xhr.onload = e => resolve(JSON.parse(e.target.responseText))
            xhr.onerror = reject;
            if (xhr.upload && onProgress) {
                xhr.upload.onprogress = onProgress;
            }
            if ('onprogerss' in xhr && onProgress) {
                xhr.onprogress = onProgress;
            }
            xhr.send(opts.body)
        })
    }

    static check(result) {
        if (result.code !== 0) {
            return Promise.reject(result);
        }
        return result.object;
    }

    static json(response) {
        if (response.status >= 200 && response.status < 300) {
            try {
                return response.json();
            } catch (e) {
                throw e;
            }
        } else if (response.status === 401) {

        }
        return Promise.reject({
            code: -1,
            data: null,
            message: "HTTP-ERROR",
            response
        });
    }
}
