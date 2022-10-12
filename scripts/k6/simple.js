import http from 'k6/http';
import { sleep } from 'k6';


const INTERNAL_BASE_URL = 'http://localhost:16790/api/v1';
const EXTERNAL_BASE_URL = __ENV.SEPARATE_INTERNAL_EXTERNAL_PORTS ? 'http://localhost:16789/api/v1' : INTERNAL_BASE_URL;


export default function () {
	const endpoints = [
		'algorithms',
		'provider',
		'system',
		'getToplogy',
		'currentModel',
	];
	const internalRequests = endpoints.map(endpoint => {
		return {
			method: 'GET',
			url: `${INTERNAL_BASE_URL}/${endpoint}`,
		};
	});
	const externalRequests = endpoints.map(endpoint => {
		return {
			method: 'GET',
			url: `${EXTERNAL_BASE_URL}/${endpoint}`,
		};
	});

	let responses = http.batch([...internalRequests, ...externalRequests]);
	sleep(0.1);
}
