import http from 'k6/http';
import { sleep } from 'k6';


const BASE_URL = 'http://localhost:16789/api/v1';

export default function () {
	const endpoints = [
		'algorithms',
		'provider',
		'system',
		'getToplogy',
		'currentModel',
	];
	const requests = endpoints.map(endpoint => {
		return {
			method: 'GET',
			url: `${BASE_URL}/${endpoint}`,
		};
	});

	let responses = http.batch(requests);
	sleep(0.1);
}
